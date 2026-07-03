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
package com.opssage.knowledge.repository

import com.opssage.knowledge.model.KnownIncident
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import org.springframework.stereotype.Repository

@Repository
class MongoKnownIncidentRepository(
    private val repository: KnownIncidentMongoRepository,
) : KnownIncidentRepository {

    override fun findById(id: String): Mono<KnownIncident> =
        repository.findById(id)

    override fun findAll(): Flux<KnownIncident> = repository.findAll()

    override fun findByServiceId(serviceId: String): Flux<KnownIncident> =
        repository.findByServiceId(serviceId)

    override fun findByTitleContainingIgnoreCase(
        keyword: String,
    ): Flux<KnownIncident> = repository.findByTitleContainingIgnoreCase(keyword)

    override fun findByRelatedServicesContains(
        serviceId: String,
    ): Flux<KnownIncident> = repository.findByRelatedServicesContains(serviceId)

    override fun save(incident: KnownIncident): Mono<KnownIncident> =
        repository.save(incident)

    override fun deleteById(id: String): Mono<Void> = repository.deleteById(id)
}
