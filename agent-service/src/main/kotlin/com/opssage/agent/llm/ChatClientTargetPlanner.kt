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

import com.opssage.agent.catalog.ServiceAliases
import com.opssage.agent.catalog.ServiceCatalog
import com.opssage.agent.config.SreProperties
import com.opssage.agent.masking.PiiMasker
import com.opssage.agent.model.InvestigationTarget
import io.github.oshai.kotlinlogging.KotlinLogging
import tools.jackson.databind.ObjectMapper

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class ChatClientTargetPlanner(
    @Qualifier("toolFreeChatClient")
    private val chatClient: ChatClient,
    private val mapper: ObjectMapper,
    private val masker: PiiMasker,
    private val historyMasker: HistoryMasker,
    private val catalog: ServiceCatalog,
    private val properties: SreProperties,
) : TargetPlanner {

    override fun plan(
        history: List<ConversationTurn>,
        userInput: String,
    ): InvestigationTarget? {
        val known = catalog.services()
        val content =
            LlmCalls.guarded {
                chatClient
                    .prompt()
                    .system(PLAN_SYSTEM)
                    .messages(historyMasker.toMessages(history))
                    .user(PLAN_INSTRUCTION + masker.mask(userInput).text)
                    .options(schemaOptions(known))
                    .call()
                    .content()
            }
        return toTarget(content, known)
    }

    private fun schemaOptions(known: List<String>): OpenAiChatOptions.Builder =
        OpenAiChatOptions
            .builder()
            .outputSchema(schema(serviceNode(known)))

    private fun serviceNode(known: List<String>): String {
        if (known.isEmpty()) {
            return """{"type": "string"}"""
        }
        val allowed = mapper.writeValueAsString(known + UNRESOLVED)
        return """{"type": "string", "enum": $allowed}"""
    }

    private fun toTarget(
        content: String?,
        known: List<String>,
    ): InvestigationTarget? {
        val planned = parseService(content) ?: return unresolved()
        val service = resolveService(planned, known) ?: return unresolved()
        if (!isToolSafe(planned)) {
            log.atInfo {
                message = "Planner named a service the tools cannot query"
                payload = mapOf("chars" to planned.length)
            }
            return unresolved()
        }
        log.atInfo {
            message = "Resolved investigation target"
            payload =
                mapOf(
                    "service" to service,
                    "namespace" to properties.namespace,
                )
        }
        return InvestigationTarget(service, properties.namespace)
    }

    private fun resolveService(
        planned: String,
        known: List<String>,
    ): String? {
        if (known.isEmpty()) {
            return planned
        }
        ServiceAliases.resolve(planned, known)?.let { return it }
        log.atInfo {
            message = "Planner named a service outside the catalog"
            payload = mapOf("service" to planned)
        }
        return null
    }

    private fun parseService(content: String?): String? {
        val json = JsonPayloads.extractObject(content) ?: return null
        val planned =
            runCatching {
                mapper.readValue(json, PlannedService::class.java)
            }.getOrElse { error ->
                log.atWarn {
                    message = "Planner output is not valid JSON"
                    cause = error
                }
                null
            }
        return planned?.service?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun isToolSafe(service: String): Boolean =
        service.length <= MAX_SERVICE_LENGTH && service.all(::isToolSafeChar)

    private fun isToolSafeChar(symbol: Char): Boolean =
        symbol in 'a'..'z' ||
            symbol in 'A'..'Z' ||
            symbol in '0'..'9' ||
            symbol in ALLOWED_SYMBOLS

    private fun unresolved(): InvestigationTarget? {
        log.atInfo {
            message = "Investigation target is unresolved, skipping playbook"
        }
        return null
    }

    private companion object {
        const val MAX_SERVICE_LENGTH = 253
        const val ALLOWED_SYMBOLS = "_.:-"
        const val UNRESOLVED = ""

        val PLAN_SYSTEM =
            """
            Ты извлекаешь цель расследования из запроса дежурного инженера.
            Не вызывай инструменты и ничего не объясняй. Верни только
            JSON-объект (service).
            """.trimIndent()

        val PLAN_INSTRUCTION =
            "Выбери сервис, о котором идёт речь в запросе ниже. Отвечай " +
                "техническим именем сервиса из телеметрии, даже если в " +
                "запросе он назван по-русски или описательно. Если понять " +
                "сервис нельзя — верни пустую строку.\n\n"

        fun schema(serviceNode: String): String =
            """
            {
              "type": "object",
              "additionalProperties": false,
              "properties": {
                "service": $serviceNode
              },
              "required": ["service"]
            }
            """.trimIndent()
    }
}
