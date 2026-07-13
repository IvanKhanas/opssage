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
import com.opssage.agent.model.Observation
import io.github.oshai.kotlinlogging.KotlinLogging

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class ChatClientLlmClient(
    private val chatClient: ChatClient,
    @Qualifier("toolFreeChatClient")
    private val toolFreeChatClient: ChatClient,
    private val masker: PiiMasker,
    private val historyMasker: HistoryMasker,
    private val parser: LlmVerdictParser,
    private val properties: LlmProperties,
) : LlmClient {

    override fun investigate(
        systemPrompt: String,
        history: List<ConversationTurn>,
        userInput: String,
        observations: List<Observation>,
    ): LlmVerdict {
        log.atInfo {
            message = "Dispatching investigation to external LLM"
            payload =
                mapOf(
                    "inputChars" to userInput.length,
                    "historyTurns" to history.size,
                    "observations" to observations.size,
                )
        }
        val findings = reason(systemPrompt, history, userInput, observations)
        return finalize(findings, observations)
    }

    private fun reason(
        systemPrompt: String,
        history: List<ConversationTurn>,
        userInput: String,
        observations: List<Observation>,
    ): String =
        dispatch {
            chatClient
                .prompt()
                .system(systemPrompt)
                .messages(historyMasker.toMessages(history))
                .user(masker.mask(userInput).text + render(observations))
                .call()
                .content()
        }.orEmpty()

    private fun render(observations: List<Observation>): String {
        if (observations.isEmpty()) {
            return ""
        }
        return observations.joinToString(
            separator = "\n\n",
            prefix = OBSERVATIONS_HEADER,
        ) { observation ->
            "### ${observation.tool}\n" +
                observation.output.take(properties.observationMaxChars)
        }
    }

    private fun finalize(
        findings: String,
        observations: List<Observation>,
    ): LlmVerdict {
        val draft =
            dispatch {
                toolFreeChatClient
                    .prompt()
                    .system(FINALIZE_SYSTEM)
                    .user(finalizeInput(findings, observations))
                    .options(structuredOutputOptions())
                    .call()
                    .content()
            }
        parser.parse(draft)?.let { return it }
        return repair(draft)
    }

    private fun repair(draft: String?): LlmVerdict {
        val repaired =
            dispatch {
                toolFreeChatClient
                    .prompt()
                    .system(FINALIZE_SYSTEM)
                    .user(REPAIR_INSTRUCTION + draft.orEmpty())
                    .options(structuredOutputOptions())
                    .call()
                    .content()
            }
        return parser.parse(repaired) ?: fallback(draft)
    }

    private fun finalizeInput(
        findings: String,
        observations: List<Observation>,
    ): String =
        FINALIZE_INSTRUCTION +
            "Промежуточный вывод модели:\n\n" +
            masker.mask(findings).text +
            render(observations)

    private fun fallback(draft: String?): LlmVerdict {
        log.atWarn {
            message = "Falling back to low-confidence summary"
            payload = mapOf("outputChars" to (draft?.length ?: 0))
        }
        val summary =
            draft
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.take(properties.fallbackSummaryMaxChars)
                ?: EMPTY_SUMMARY
        return LlmVerdict(summary, Confidence.LOW, emptyList())
    }

    private fun dispatch(call: () -> String?): String? = LlmCalls.guarded(call)

    private companion object {
        const val EMPTY_SUMMARY = "Модель не вернула структурированный вывод."

        val OBSERVATIONS_HEADER =
            "\n\nСобранные данные (фиксированный план расследования):\n\n"

        val FINALIZE_SYSTEM =
            """
            Ты оформляешь итог уже проведённого расследования в строгий
            JSON по схеме (summary, confidence, evidence). Не вызывай
            инструменты. Верни только JSON-объект без Markdown, префиксов
            и пояснений вокруг. Уровень уверенности указывай только в поле
            confidence: в тексте summary его называть нельзя, потому что
            итоговое значение пересчитывается по собранным данным.
            """.trimIndent()

        val FINALIZE_INSTRUCTION =
            "Сформируй итоговый JSON по данным расследования ниже.\n\n"

        val REPAIR_INSTRUCTION =
            "Предыдущий ответ не был валидным JSON по схеме. Верни ТОЛЬКО " +
                "валидный JSON-объект (summary, confidence, evidence) без " +
                "текста вокруг. Ответ для исправления:\n\n"

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
