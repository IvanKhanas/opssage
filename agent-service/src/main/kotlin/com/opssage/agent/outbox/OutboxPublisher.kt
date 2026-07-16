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
package com.opssage.agent.outbox

import com.opssage.agent.config.OutboxProperties
import io.github.oshai.kotlinlogging.KotlinLogging

import java.util.concurrent.TimeUnit

import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class OutboxPublisher(
    private val outbox: OutboxService,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val properties: OutboxProperties,
) {

    @Scheduled(fixedDelayString = "\${agent.outbox.scheduler-interval}")
    fun publishScheduled() {
        publishPending()
    }

    fun publishPending(): Int {
        val events = outbox.claimPending(properties.batchSize)
        if (events.isEmpty()) return 0
        val published = mutableListOf<OutboxEvent>()
        events.forEach { event ->
            try {
                kafkaTemplate
                    .send(
                        event.message.destination.topic,
                        event.message.destination.key,
                        event.message.payload,
                    ).get(
                        properties.publishing.sendTimeout.toMillis(),
                        TimeUnit.MILLISECONDS,
                    )
                published.add(event)
            } catch (error: Exception) {
                outbox.recordFailure(event, error)
                log.atWarn {
                    message = "Outbox event publish failed"
                    cause = error
                    payload =
                        mapOf(
                            "eventId" to event.id,
                            "topic" to event.message.destination.topic,
                            "eventType" to event.message.eventType,
                        )
                }
            }
        }
        outbox.markPublished(published)
        return published.size
    }
}
