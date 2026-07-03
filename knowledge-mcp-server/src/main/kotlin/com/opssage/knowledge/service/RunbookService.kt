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
package com.opssage.knowledge.service

import com.opssage.knowledge.model.Runbook
import com.opssage.knowledge.repository.RunbookRepository
import com.opssage.knowledge.util.orNotFound
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import java.time.Instant

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private typealias Runs = Flux<Runbook>

@Service
class RunbookService(
    private val repo: RunbookRepository,
) {

    fun findById(id: String): Mono<Runbook> =
        repo
            .findById(id)
            .orNotFound("Runbook", id)

    fun findByService(serviceId: String): Runs = repo.findByServiceId(serviceId)

    fun findByAlert(alertName: String): Runs = repo.findByAlertName(alertName)

    fun findAll(): Runs = repo.findAll()

    fun create(runbook: Runbook): Mono<Runbook> = repo.save(runbook)

    @Transactional
    fun update(
        id: String,
        runbook: Runbook,
    ): Mono<Runbook> {
        val existing =
            repo
                .findById(id)
                .orNotFound("Runbook", id)

        return existing.flatMap { stored ->
            repo.save(
                runbook.copy(
                    id = stored.id,
                    createdAt = stored.createdAt,
                    updatedAt = Instant.now(),
                ),
            )
        }
    }

    @Transactional
    fun delete(id: String): Mono<Void> {
        val existing =
            repo
                .findById(id)
                .orNotFound("Runbook", id)

        return existing.flatMap { repo.deleteById(id) }
    }
}
