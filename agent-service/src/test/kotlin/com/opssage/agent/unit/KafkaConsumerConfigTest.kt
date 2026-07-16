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
import com.opssage.agent.config.KafkaConsumerConfig
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import org.springframework.kafka.core.KafkaTemplate

class KafkaConsumerConfigTest {

    @Test
    fun `creates string kafka listener factory with dlq handler`() {
        val config = KafkaConsumerConfig()
        val consumerFactory =
            config.consumerFactory(
                bootstrapServers = "localhost:9092",
                groupId = "agent-service",
                offset = "earliest",
            )
        val errorHandler =
            config.kafkaErrorHandler(
                mockk<KafkaTemplate<String, String>>(relaxed = true),
                kafkaProperties(),
            )

        val listenerFactory =
            config.kafkaListenerContainerFactory(
                consumerFactory,
                errorHandler,
            )

        assertThat(listenerFactory).isNotNull()
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
}
