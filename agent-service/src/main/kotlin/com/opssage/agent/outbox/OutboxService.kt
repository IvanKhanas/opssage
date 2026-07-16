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

import java.time.Clock

import org.springframework.stereotype.Service

@Service
class OutboxService(
    private val repository: OutboxEventRepository,
    private val properties: OutboxProperties,
    private val clock: Clock,
) {

    fun enqueue(message: OutboxMessage): OutboxEvent =
        repository.save(
            OutboxEvent(
                message = message,
                state =
                    OutboxState(
                        timing = OutboxTiming(createdAt = clock.instant()),
                    ),
            ),
        )

    fun claimPending(limit: Int): List<OutboxEvent> =
        repository.claimPending(
            limit,
            properties.publishing.leaseDuration,
        )

    fun markPublished(events: List<OutboxEvent>) {
        val now = clock.instant()
        repository.saveAll(
            events.map {
                it.copy(
                    state =
                        it.state.copy(
                            status = OutboxStatus.PUBLISHED,
                            timing =
                                it.state.timing.copy(
                                    publishedAt = now,
                                ),
                            delivery = OutboxDelivery(),
                        ),
                )
            },
        )
    }

    fun recordFailure(
        event: OutboxEvent,
        error: Throwable,
    ) {
        val attempts = event.state.delivery.failure.attempts + 1
        val status =
            if (attempts >= properties.retry.maxAttempts) {
                OutboxStatus.DEAD
            } else {
                OutboxStatus.PENDING
            }
        repository.save(
            event.copy(
                state =
                    event.state.copy(
                        status = status,
                        delivery =
                            event.state.delivery.copy(
                                retry =
                                    OutboxRetry(
                                        nextRetryAt =
                                            clock.instant().plus(
                                                properties.retry.retryDelay,
                                            ),
                                    ),
                                lease = OutboxLease(),
                                failure =
                                    OutboxFailure(
                                        attempts = attempts,
                                        lastError =
                                            error.message
                                                ?.take(MAX_ERROR_LENGTH),
                                    ),
                            ),
                    ),
            ),
        )
    }

    private companion object {
        const val MAX_ERROR_LENGTH = 1_000
    }
}
