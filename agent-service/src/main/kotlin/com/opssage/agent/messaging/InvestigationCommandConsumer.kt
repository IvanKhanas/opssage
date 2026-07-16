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

import com.opssage.agent.investigation.InvestigationReport
import com.opssage.agent.investigation.InvestigationService
import io.github.oshai.kotlinlogging.KotlinLogging
import tools.jackson.databind.ObjectMapper

import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}
private const val INVESTIGATION_REQUESTS_TOPIC =
    "\${agent.kafka.topics.investigations.investigation-requests-topic}"
private const val INVESTIGATION_DEDUP_PREFIX = "investigation:"

@Component
class InvestigationCommandConsumer(
    private val investigationService: InvestigationService,
    private val resultPublisher: InvestigationResultPublisher,
    private val objectMapper: ObjectMapper,
    private val idempotencyGuard: IdempotencyGuard,
) {

    @KafkaListener(topics = [INVESTIGATION_REQUESTS_TOPIC])
    fun consume(message: String) {
        val command =
            objectMapper.readValue(
                message,
                InvestigationCommand::class.java,
            )
        val dedupKey =
            "$INVESTIGATION_DEDUP_PREFIX${command.metadata.requestId}"
        if (!idempotencyGuard.tryStart(dedupKey)) {
            logSkipped(command.metadata.requestId)
            return
        }
        runInvestigation(command)
    }

    private fun logSkipped(requestId: String) {
        log.atInfo {
            message = "Skipping already-processed investigation command"
            payload = mapOf("requestId" to requestId)
        }
    }

    private fun runInvestigation(command: InvestigationCommand) {
        try {
            val report =
                investigationService.investigate(
                    command.toInvestigationRequest(),
                )
            resultPublisher.publish(
                report.toResultEvent(command.metadata.requestId),
            )
            logCompleted(command, report)
        } catch (error: Exception) {
            resultPublisher.publish(
                failedResultEvent(command.metadata.requestId),
            )
            log.atWarn {
                message = "Kafka investigation failed"
                cause = error
                payload =
                    mapOf(
                        "requestId" to command.metadata.requestId,
                        "type" to command.request.investigationType,
                    )
            }
        }
    }

    private fun logCompleted(
        command: InvestigationCommand,
        report: InvestigationReport,
    ) {
        log.atInfo {
            message = "Kafka investigation completed"
            payload =
                mapOf(
                    "requestId" to command.metadata.requestId,
                    "conversationId" to report.conversationId,
                    "type" to report.investigationType,
                    "confidence" to report.confidence,
                )
        }
    }
}
