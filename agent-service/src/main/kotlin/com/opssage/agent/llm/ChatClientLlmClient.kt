/*
 * Copyright 2026 Ivan Khanas
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.opssage.agent.llm

import com.opssage.agent.config.LlmProperties
import com.opssage.agent.masking.PiiMasker
import com.opssage.agent.model.Confidence
import io.github.oshai.kotlinlogging.KotlinLogging
import tools.jackson.databind.ObjectMapper

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException

private val log = KotlinLogging.logger {}

@Component
class ChatClientLlmClient(
    private val chatClient: ChatClient,
    private val mapper: ObjectMapper,
    private val masker: PiiMasker,
    private val properties: LlmProperties,
) : LlmClient {

    override fun investigate(
        systemPrompt: String,
        history: List<ConversationTurn>,
        userInput: String,
    ): LlmVerdict {
        log.atInfo {
            message = "Dispatching investigation to external LLM"
            payload =
                mapOf(
                    "inputChars" to userInput.length,
                    "historyTurns" to history.size,
                )
        }
        val content =
            try {
                chatClient
                    .prompt()
                    .system(systemPrompt)
                    .messages(history.map { toMessage(it) })
                    .user(masker.mask(userInput).text)
                    .options(structuredOutputOptions())
                    .call()
                    .content()
            } catch (ex: RestClientException) {
                throw LlmInvocationException(
                    "Failed to reach the external LLM",
                    ex,
                )
            }
        return parseVerdict(content)
    }

    private fun parseVerdict(content: String?): LlmVerdict {
        if (content.isNullOrBlank()) {
            return EMPTY_VERDICT
        }
        return runCatching {
            mapper.readValue(jsonObject(content), LlmVerdict::class.java)
        }.getOrElse { error ->
            log.atWarn {
                message = "Model returned non-JSON investigation output"
                payload = mapOf("outputChars" to content.length)
                cause = error
            }
            LlmVerdict(
                summary =
                    content.trim().take(properties.fallbackSummaryMaxChars),
                confidence = Confidence.LOW,
                evidence = emptyList(),
            )
        }
    }

    private fun jsonObject(content: String): String {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        require(start >= 0 && end > start) {
            "Model output does not contain a JSON object"
        }
        return content.substring(start, end + 1)
    }

    private fun toMessage(turn: ConversationTurn): Message {
        val masked = masker.mask(turn.content).text
        return when (turn.role) {
            TurnRole.USER -> UserMessage(masked)
            TurnRole.ASSISTANT -> AssistantMessage(masked)
        }
    }

    private companion object {
        val EMPTY_VERDICT =
            LlmVerdict(
                summary = "Модель не вернула структурированный вывод.",
                confidence = Confidence.LOW,
                evidence = emptyList(),
            )

        fun structuredOutputOptions(): OpenAiChatOptions.Builder =
            OpenAiChatOptions
                .builder()
                .outputSchema(
                    """
                    {
                      "type": "object",
                      "additionalProperties": false,
                      "properties": {
                        "summary": {
                          "type": "string"
                        },
                        "confidence": {
                          "type": "string",
                          "enum": ["LOW", "MEDIUM", "HIGH"]
                        },
                        "evidence": {
                          "type": "array",
                          "items": {
                            "type": "string"
                          }
                        }
                      },
                      "required": ["summary", "confidence", "evidence"]
                    }
                    """.trimIndent(),
                )
    }
}
