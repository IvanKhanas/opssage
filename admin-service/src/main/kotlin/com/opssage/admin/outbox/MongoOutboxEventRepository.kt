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
package com.opssage.admin.outbox

import java.time.Clock
import java.time.Duration
import java.time.Instant

import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository

@Repository
class MongoOutboxEventRepository(
    private val data: OutboxEventMongoRepository,
    private val mongo: MongoTemplate,
    private val clock: Clock,
) : OutboxEventRepository {

    override fun claimPending(
        limit: Int,
        leaseDuration: Duration,
    ): List<OutboxEvent> =
        generateSequence { claimOne(leaseDuration) }
            .take(limit)
            .toList()

    override fun save(event: OutboxEvent): OutboxEvent = data.save(event)

    override fun saveAll(events: List<OutboxEvent>): List<OutboxEvent> =
        data.saveAll(events)

    private fun claimOne(leaseDuration: Duration): OutboxEvent? {
        val now = clock.instant()
        val query =
            Query(availableCriteria(now))
                .with(Sort.by("state.timing.createdAt").ascending())
        val update =
            Update()
                .set(STATUS_FIELD, OutboxStatus.IN_PROGRESS)
                .set(LOCKED_UNTIL_FIELD, now.plus(leaseDuration))
        return mongo.findAndModify(
            query,
            update,
            FindAndModifyOptions.options().returnNew(true),
            OutboxEvent::class.java,
        )
    }

    private fun availableCriteria(now: Instant): Criteria =
        Criteria().orOperator(
            pendingCriteria(now),
            expiredLeaseCriteria(now),
        )

    private fun pendingCriteria(now: Instant): Criteria =
        Criteria().andOperator(
            Criteria.where(STATUS_FIELD).`is`(OutboxStatus.PENDING),
            Criteria().orOperator(
                Criteria.where(NEXT_RETRY_FIELD).exists(false),
                Criteria.where(NEXT_RETRY_FIELD).`is`(null),
                Criteria.where(NEXT_RETRY_FIELD).lte(now),
            ),
        )

    private fun expiredLeaseCriteria(now: Instant): Criteria =
        Criteria().andOperator(
            Criteria.where(STATUS_FIELD).`is`(OutboxStatus.IN_PROGRESS),
            Criteria.where(LOCKED_UNTIL_FIELD).lte(now),
        )

    private companion object {
        const val STATUS_FIELD = "state.status"
        const val NEXT_RETRY_FIELD = "state.delivery.retry.nextRetryAt"
        const val LOCKED_UNTIL_FIELD = "state.delivery.lease.lockedUntil"
    }
}
