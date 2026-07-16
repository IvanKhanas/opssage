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
import com.opssage.admin.outbox.OutboxDelivery
import com.opssage.admin.outbox.OutboxDestination
import com.opssage.admin.outbox.OutboxEvent
import com.opssage.admin.outbox.OutboxEventRepository
import com.opssage.admin.outbox.OutboxFailure
import com.opssage.admin.outbox.OutboxMessage
import com.opssage.admin.outbox.OutboxService
import com.opssage.admin.outbox.OutboxState
import com.opssage.admin.outbox.OutboxStatus
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

@ExtendWith(MockKExtension::class)
class OutboxServiceTest {

    @MockK
    lateinit var repository: OutboxEventRepository

    private val saved = slot<OutboxEvent>()
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
    private val clock =
        Clock.fixed(
            Instant.parse("2026-07-13T12:00:00Z"),
            ZoneOffset.UTC,
        )

    @Test
    fun `enqueues pending event with current timestamp`() {
        every { repository.save(capture(saved)) } answers { saved.captured }
        val service = OutboxService(repository, properties, clock)

        val event = service.enqueue(message())

        assertThat(event.state.status).isEqualTo(OutboxStatus.PENDING)
        assertThat(event.state.timing.createdAt).isEqualTo(clock.instant())
        assertThat(event.state.delivery.failure.attempts).isZero()
    }

    @Test
    fun `claims pending events through repository`() {
        every { repository.claimPending(10, Duration.ofMinutes(2)) } returns
            listOf(event("ready"))
        val service = OutboxService(repository, properties, clock)

        val pending = service.claimPending(10)

        assertThat(pending.map { it.id }).containsExactly("ready")
    }

    @Test
    fun `marks events as published`() {
        every { repository.saveAll(any<List<OutboxEvent>>()) } answers {
            firstArg()
        }
        val service = OutboxService(repository, properties, clock)

        service.markPublished(listOf(event("event-1")))

        verify {
            repository.saveAll(
                match<List<OutboxEvent>> {
                    isPublishedAtCurrentTime(it.single())
                },
            )
        }
    }

    @Test
    fun `records dead event after max attempts`() {
        every { repository.save(capture(saved)) } answers { saved.captured }
        val state =
            OutboxState(
                delivery =
                    OutboxDelivery(
                        failure =
                            OutboxFailure(
                                attempts = 9,
                                lastError = "previous",
                            ),
                    ),
            )
        val service = OutboxService(repository, properties, clock)

        service.recordFailure(event("event-1", state), RuntimeException("x"))

        assertThat(saved.captured.state.status).isEqualTo(OutboxStatus.DEAD)
        assertThat(saved.captured.state.delivery.failure.attempts)
            .isEqualTo(10)
        assertThat(saved.captured.state.delivery.failure.lastError)
            .isEqualTo("x")
        assertThat(saved.captured.state.delivery.retry.nextRetryAt)
            .isEqualTo(clock.instant().plusSeconds(30))
    }

    private fun event(
        id: String,
        state: OutboxState = OutboxState(),
    ): OutboxEvent =
        OutboxEvent(
            id = id,
            message = message(),
            state = state,
        )

    private fun message(): OutboxMessage =
        OutboxMessage(
            destination =
                OutboxDestination(
                    topic = "topic",
                    key = "key",
                ),
            payload = "{}",
            eventType = "TestEvent",
        )

    private fun isPublishedAtCurrentTime(event: OutboxEvent): Boolean =
        event.state.status == OutboxStatus.PUBLISHED &&
            event.state.timing.publishedAt == clock.instant()
}
