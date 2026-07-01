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

import com.opssage.knowledge.model.ServiceProfile
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import org.springframework.stereotype.Repository

@Repository
class MongoServiceProfileRepository(
    private val repository: ServiceProfileMongoRepository,
) : ServiceProfileRepository {

    override fun findById(id: String): Mono<ServiceProfile> =
        repository.findById(id)

    override fun findAll(): Flux<ServiceProfile> = repository.findAll()

    override fun findByServiceId(serviceId: String): Mono<ServiceProfile> =
        repository.findByServiceId(serviceId)

    override fun save(profile: ServiceProfile): Mono<ServiceProfile> =
        repository.save(profile)

    override fun deleteById(id: String): Mono<Void> = repository.deleteById(id)
}
