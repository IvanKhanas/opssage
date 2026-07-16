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

import java.time.Instant

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document("outbox_events")
data class OutboxEvent(
    @Id
    val id: String? = null,
    val message: OutboxMessage,
    val state: OutboxState = OutboxState(),
)

data class OutboxMessage(
    val destination: OutboxDestination,
    val payload: String,
    val eventType: String,
)

data class OutboxDestination(
    val topic: String,
    val key: String,
)

data class OutboxState(
    @Indexed
    val status: OutboxStatus = OutboxStatus.PENDING,
    val timing: OutboxTiming = OutboxTiming(),
    val delivery: OutboxDelivery = OutboxDelivery(),
)

data class OutboxTiming(
    @Indexed
    val createdAt: Instant = Instant.now(),
    @Indexed
    val publishedAt: Instant? = null,
)

data class OutboxDelivery(
    val retry: OutboxRetry = OutboxRetry(),
    val lease: OutboxLease = OutboxLease(),
    val failure: OutboxFailure = OutboxFailure(),
)

data class OutboxRetry(
    @Indexed
    val nextRetryAt: Instant? = null,
)

data class OutboxLease(
    @Indexed
    val lockedUntil: Instant? = null,
)

data class OutboxFailure(
    val attempts: Int = 0,
    val lastError: String? = null,
)

enum class OutboxStatus {
    PENDING,
    IN_PROGRESS,
    PUBLISHED,
    DEAD,
}
