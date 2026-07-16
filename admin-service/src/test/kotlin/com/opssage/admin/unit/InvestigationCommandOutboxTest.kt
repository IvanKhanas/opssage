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
package com.opssage.admin.unit

import com.opssage.admin.config.AdminInvestigationTopics
import com.opssage.admin.config.AdminKafkaDlqProperties
import com.opssage.admin.config.AdminKafkaProperties
import com.opssage.admin.config.AdminKafkaTopics
import com.opssage.admin.config.AdminMessageTopics
import com.opssage.admin.messaging.InvestigationCommand
import com.opssage.admin.messaging.InvestigationCommandMetadata
import com.opssage.admin.messaging.InvestigationCommandOutbox
import com.opssage.admin.messaging.InvestigationCommandRequest
import com.opssage.admin.messaging.InvestigationCommandWindow
import com.opssage.admin.messaging.InvestigationRequester
import com.opssage.admin.model.InvestigationType
import com.opssage.admin.outbox.OutboxMessage
import com.opssage.admin.outbox.OutboxService
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

import java.time.Duration
import java.time.Instant

@ExtendWith(MockKExtension::class)
class InvestigationCommandOutboxTest {

    @MockK
    lateinit var outbox: OutboxService

    private val message = slot<OutboxMessage>()

    @Test
    fun `enqueues request command without user email`() {
        every { outbox.enqueue(capture(message)) } returns
            io.mockk.mockk()
        val objectMapper = jacksonObjectMapper()
        val publisher =
            InvestigationCommandOutbox(
                outbox,
                objectMapper,
                kafkaProperties(),
            )

        publisher.enqueue(command())

        assertThat(message.captured.destination.topic)
            .isEqualTo("opssage.investigation.requests")
        assertThat(message.captured.destination.key).isEqualTo("req-1")
        assertThat(message.captured.eventType)
            .isEqualTo("InvestigationRequested")
        val payload =
            objectMapper.readValue<InvestigationCommand>(
                message.captured.payload,
            )
        assertThat(payload.metadata.requestedBy.userId).isEqualTo("user-1")
        assertThat(message.captured.payload).doesNotContain("sre@example.com")
        verify { outbox.enqueue(any()) }
    }

    private fun kafkaProperties(): AdminKafkaProperties =
        AdminKafkaProperties(
            topics = adminTopics(),
            dlq =
                AdminKafkaDlqProperties(
                    investigationResultsTopic =
                        "opssage.investigation.results.dlq",
                    investigationMessageResultsTopic =
                        "opssage.investigation.message.results.dlq",
                ),
        )

    private fun adminTopics(): AdminKafkaTopics =
        AdminKafkaTopics(
            investigations =
                AdminInvestigationTopics(
                    investigationRequestsTopic =
                        "opssage.investigation.requests",
                    investigationResultsTopic =
                        "opssage.investigation.results",
                ),
            messages =
                AdminMessageTopics(
                    investigationMessagesTopic =
                        "opssage.investigation.messages",
                    investigationMessageResultsTopic =
                        "opssage.investigation.message.results",
                ),
        )

    private fun command(): InvestigationCommand =
        InvestigationCommand(
            metadata =
                InvestigationCommandMetadata(
                    requestId = "req-1",
                    requestedAt = Instant.parse("2026-07-13T12:00:00Z"),
                    requestedBy =
                        InvestigationRequester(
                            userId = "user-1",
                            roles = setOf("SRE"),
                        ),
                ),
            request =
                InvestigationCommandRequest(
                    title = "deposit alert",
                    investigationType = InvestigationType.ALERT_INVESTIGATION,
                    input = "HighErrorRate fired",
                ),
            window =
                InvestigationCommandWindow(
                    from = null,
                    lookback = Duration.ofHours(4),
                ),
        )
}
