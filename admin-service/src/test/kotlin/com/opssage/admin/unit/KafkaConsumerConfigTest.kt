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
import com.opssage.admin.config.KafkaConsumerConfig
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import org.springframework.kafka.core.KafkaTemplate

class KafkaConsumerConfigTest {

    @Test
    fun `creates string kafka listener factory`() {
        val config = KafkaConsumerConfig()
        val consumerFactory =
            config.consumerFactory(
                bootstrapServers = "localhost:9092",
                groupId = "admin-service",
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
}
