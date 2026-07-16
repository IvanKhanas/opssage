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
package com.opssage.admin.config

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff

@Configuration
class KafkaConsumerConfig {

    @Bean
    fun consumerFactory(
        @Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String,
        @Value("\${spring.kafka.consumer.group-id}") groupId: String,
        @Value("\${spring.kafka.consumer.auto-offset-reset}") offset: String,
    ): ConsumerFactory<String, String> =
        DefaultKafkaConsumerFactory(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to groupId,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to offset,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to
                    StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to
                    StringDeserializer::class.java,
            ),
        )

    @Bean
    fun kafkaListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, String>,
        errorHandler: DefaultErrorHandler,
    ): ConcurrentKafkaListenerContainerFactory<String, String> =
        ConcurrentKafkaListenerContainerFactory<String, String>().apply {
            setConsumerFactory(consumerFactory)
            setCommonErrorHandler(errorHandler)
        }

    @Bean
    fun kafkaErrorHandler(
        kafkaTemplate: KafkaTemplate<String, String>,
        properties: AdminKafkaProperties,
    ): DefaultErrorHandler =
        DefaultErrorHandler(
            DeadLetterPublishingRecoverer(kafkaTemplate) { record, _ ->
                TopicPartition(
                    adminDlqTopic(record.topic(), properties),
                    record.partition(),
                )
            },
            FixedBackOff(RETRY_INTERVAL_MS, RETRY_ATTEMPTS),
        )

    private companion object {
        const val RETRY_INTERVAL_MS = 1_000L
        const val RETRY_ATTEMPTS = 3L
    }

    private fun adminDlqTopic(
        topic: String,
        properties: AdminKafkaProperties,
    ): String =
        if (
            topic ==
            properties.topics.messages.investigationMessageResultsTopic
        ) {
            properties.dlq.investigationMessageResultsTopic
        } else {
            properties.dlq.investigationResultsTopic
        }
}
