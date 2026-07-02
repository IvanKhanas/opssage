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

import com.opssage.knowledge.model.ServiceProfile
import com.opssage.knowledge.repository.ServiceProfileRepository
import com.opssage.knowledge.util.orNotFound
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import java.time.Instant

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private typealias Profile = Mono<ServiceProfile>

@Service
class ServiceProfileService(
    private val repo: ServiceProfileRepository,
) {

    fun findById(id: String): Profile =
        repo
            .findById(id)
            .orNotFound(RESOURCE, id)

    fun findByServiceId(id: String): Profile = repo.findByServiceId(id)

    fun findByServiceIdOrThrow(serviceId: String): Profile =
        repo
            .findByServiceId(serviceId)
            .orNotFound(RESOURCE, serviceId)

    fun findAll(): Flux<ServiceProfile> = repo.findAll()

    fun create(profile: ServiceProfile): Profile = repo.save(profile)

    @Transactional
    fun update(
        id: String,
        profile: ServiceProfile,
    ): Profile {
        val existing =
            repo
                .findById(id)
                .orNotFound(RESOURCE, id)

        return existing.flatMap { stored ->
            repo.save(
                profile.copy(
                    id = stored.id,
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
                .orNotFound(RESOURCE, id)

        return existing.flatMap { repo.deleteById(id) }
    }

    private companion object {
        const val RESOURCE = "Service profile"
    }
}
