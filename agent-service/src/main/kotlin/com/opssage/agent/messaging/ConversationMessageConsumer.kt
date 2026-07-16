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
package com.opssage.agent.messaging

import com.opssage.agent.investigation.InvestigationService
import io.github.oshai.kotlinlogging.KotlinLogging
import tools.jackson.databind.ObjectMapper

import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

private val messageLog = KotlinLogging.logger {}
private const val INVESTIGATION_MESSAGES_TOPIC =
    "\${agent.kafka.topics.messages.investigation-messages-topic}"
private const val MESSAGE_DEDUP_PREFIX = "message:"

@Component
class ConversationMessageConsumer(
    private val investigationService: InvestigationService,
    private val resultPublisher: ConversationMessageResultPublisher,
    private val objectMapper: ObjectMapper,
    private val idempotencyGuard: IdempotencyGuard,
) {

    @KafkaListener(topics = [INVESTIGATION_MESSAGES_TOPIC])
    fun consume(message: String) {
        val command =
            objectMapper.readValue(
                message,
                ConversationMessageCommand::class.java,
            )
        val dedupKey = "$MESSAGE_DEDUP_PREFIX${command.metadata.messageId}"
        if (!idempotencyGuard.tryStart(dedupKey)) {
            logSkipped(command.metadata.messageId)
            return
        }
        reply(command)
    }

    private fun logSkipped(messageId: String) {
        messageLog.atInfo {
            message = "Skipping already-processed conversation message"
            payload = mapOf("messageId" to messageId)
        }
    }

    private fun reply(command: ConversationMessageCommand) {
        try {
            val report =
                investigationService.reply(
                    command.body.conversation.conversationId,
                    command.body.payload.input,
                )
            resultPublisher.publish(report.toMessageResultEvent(command))
            messageLog.atInfo {
                message = "Kafka conversation message completed"
                payload =
                    mapOf(
                        "messageId" to command.metadata.messageId,
                        "requestId" to command.metadata.requestId,
                        "conversationId" to report.conversationId,
                    )
            }
        } catch (error: Exception) {
            resultPublisher.publish(failedMessageResultEvent(command))
            messageLog.atWarn {
                message = "Kafka conversation message failed"
                cause = error
                payload =
                    mapOf(
                        "messageId" to command.metadata.messageId,
                        "requestId" to command.metadata.requestId,
                    )
            }
        }
    }
}
