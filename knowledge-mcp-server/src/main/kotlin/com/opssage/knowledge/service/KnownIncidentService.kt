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

import com.opssage.knowledge.model.KnownIncident
import com.opssage.knowledge.repository.KnownIncidentRepository
import com.opssage.knowledge.util.orNotFound
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import java.time.Instant

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private typealias Incidents = Flux<KnownIncident>

@Service
class KnownIncidentService(
    private val repo: KnownIncidentRepository,
) {

    fun findById(id: String): Mono<KnownIncident> =
        repo
            .findById(id)
            .orNotFound("KnownIncident", id)

    fun findByService(serviceId: String): Incidents =
        repo.findByServiceId(serviceId)

    fun searchByTitle(keyword: String): Incidents =
        repo.findByTitleContainingIgnoreCase(keyword)

    fun findByRelatedService(serviceId: String): Incidents =
        repo.findByRelatedServicesContains(serviceId)

    fun findAll(): Incidents = repo.findAll()

    fun create(incident: KnownIncident): Mono<KnownIncident> =
        repo.save(incident)

    @Transactional
    fun update(
        id: String,
        incident: KnownIncident,
    ): Mono<KnownIncident> {
        val existing =
            repo
                .findById(id)
                .orNotFound("KnownIncident", id)

        return existing.flatMap { stored ->
            repo.save(
                incident.copy(
                    id = stored.id,
                    version = stored.version,
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
                .orNotFound("KnownIncident", id)

        return existing.flatMap { repo.deleteById(id) }
    }
}
