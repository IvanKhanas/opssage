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
package com.opssage.agent.unit

import com.opssage.agent.config.AgentInvestigationTopics
import com.opssage.agent.config.AgentKafkaDlqProperties
import com.opssage.agent.config.AgentKafkaProperties
import com.opssage.agent.config.AgentKafkaTopics
import com.opssage.agent.config.AgentMessageTopics
import com.opssage.agent.messaging.InvestigationResultEvent
import com.opssage.agent.messaging.InvestigationResultPublisher
import com.opssage.agent.messaging.InvestigationResultStatus
import com.opssage.agent.outbox.OutboxMessage
import com.opssage.agent.outbox.OutboxService
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

@ExtendWith(MockKExtension::class)
class InvestigationResultPublisherTest {

    @MockK
    lateinit var outbox: OutboxService

    private val message = slot<OutboxMessage>()

    @Test
    fun `enqueues result event through outbox`() {
        every { outbox.enqueue(capture(message)) } returns
            io.mockk.mockk()
        val objectMapper = jacksonObjectMapper()
        val publisher =
            InvestigationResultPublisher(
                outbox,
                objectMapper,
                kafkaProperties(),
            )

        publisher.publish(event())

        assertThat(message.captured.destination.topic)
            .isEqualTo("opssage.investigation.results")
        assertThat(message.captured.destination.key).isEqualTo("req-1")
        assertThat(message.captured.eventType).isEqualTo("FAILED")
        val payload =
            objectMapper.readValue<InvestigationResultEvent>(
                message.captured.payload,
            )
        assertThat(payload.status).isEqualTo(InvestigationResultStatus.FAILED)
        verify { outbox.enqueue(any()) }
    }

    private fun kafkaProperties(): AgentKafkaProperties =
        AgentKafkaProperties(
            topics = agentTopics(),
            dlq =
                AgentKafkaDlqProperties(
                    investigationRequestsTopic =
                        "opssage.investigation.requests.dlq",
                    investigationMessagesTopic =
                        "opssage.investigation.messages.dlq",
                ),
        )

    private fun agentTopics(): AgentKafkaTopics =
        AgentKafkaTopics(
            investigations =
                AgentInvestigationTopics(
                    investigationRequestsTopic =
                        "opssage.investigation.requests",
                    investigationResultsTopic =
                        "opssage.investigation.results",
                ),
            messages =
                AgentMessageTopics(
                    investigationMessagesTopic =
                        "opssage.investigation.messages",
                    investigationMessageResultsTopic =
                        "opssage.investigation.message.results",
                ),
        )

    private fun event(): InvestigationResultEvent =
        InvestigationResultEvent(
            requestId = "req-1",
            status = InvestigationResultStatus.FAILED,
        )
}
