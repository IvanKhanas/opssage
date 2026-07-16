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

import com.opssage.admin.config.OutboxProperties
import com.opssage.admin.config.OutboxPublishingProperties
import com.opssage.admin.config.OutboxRetryProperties
import com.opssage.admin.outbox.OutboxDestination
import com.opssage.admin.outbox.OutboxEvent
import com.opssage.admin.outbox.OutboxMessage
import com.opssage.admin.outbox.OutboxPublisher
import com.opssage.admin.outbox.OutboxService
import com.opssage.admin.outbox.OutboxState
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

import java.time.Duration
import java.util.concurrent.CompletableFuture

import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult

@ExtendWith(MockKExtension::class)
class OutboxPublisherTest {

    @MockK
    lateinit var outbox: OutboxService

    @MockK
    lateinit var kafkaTemplate: KafkaTemplate<String, String>

    private val properties =
        OutboxProperties(
            batchSize = 100,
            retry =
                OutboxRetryProperties(
                    maxAttempts = 10,
                    retryDelay = Duration.ofSeconds(30),
                ),
            publishing =
                OutboxPublishingProperties(
                    leaseDuration = Duration.ofMinutes(2),
                    sendTimeout = Duration.ofSeconds(5),
                ),
        )

    @Test
    fun `publishes pending events and marks them as published`() {
        every { outbox.claimPending(any()) } returns listOf(event())
        every { kafkaTemplate.send("topic", "key", "{}") } returns
            CompletableFuture.completedFuture(mockResult())
        justRun { outbox.markPublished(any()) }
        val publisher = OutboxPublisher(outbox, kafkaTemplate, properties)

        val count = publisher.publishPending()

        assertThat(count).isEqualTo(1)
        verify { outbox.markPublished(match { it.single().id == "event-1" }) }
    }

    @Test
    fun `records failed publish attempts without marking them published`() {
        every { outbox.claimPending(any()) } returns listOf(event())
        every { kafkaTemplate.send("topic", "key", "{}") } returns
            failedResult()
        justRun { outbox.recordFailure(any(), any()) }
        justRun { outbox.markPublished(emptyList()) }
        val publisher = OutboxPublisher(outbox, kafkaTemplate, properties)

        val count = publisher.publishPending()

        assertThat(count).isZero()
        verify { outbox.recordFailure(any(), any()) }
        verify { outbox.markPublished(emptyList()) }
    }

    private fun event(): OutboxEvent =
        OutboxEvent(
            id = "event-1",
            message =
                OutboxMessage(
                    destination =
                        OutboxDestination(
                            topic = "topic",
                            key = "key",
                        ),
                    payload = "{}",
                    eventType = "TestEvent",
                ),
            state = OutboxState(),
        )

    private fun mockResult(): SendResult<String, String> = io.mockk.mockk()

    private fun failedResult(): CompletableFuture<SendResult<String, String>> =
        CompletableFuture<SendResult<String, String>>().apply {
            completeExceptionally(RuntimeException("broker down"))
        }
}
