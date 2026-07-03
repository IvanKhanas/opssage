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

import com.opssage.knowledge.exception.InvalidFactStateException
import com.opssage.knowledge.exception.InvalidRequestException
import com.opssage.knowledge.model.Fact
import com.opssage.knowledge.model.FactProposal
import com.opssage.knowledge.model.FactStatus
import com.opssage.knowledge.repository.FactRepository
import com.opssage.knowledge.repository.FactVectorIndex
import com.opssage.knowledge.util.orNotFound
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import java.time.Instant

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private typealias Facts = Flux<Fact>

@Service
class FactService(
    private val repo: FactRepository,
    private val index: FactVectorIndex,
) {

    fun findById(id: String): Mono<Fact> =
        repo
            .findById(id)
            .orNotFound("Fact", id)

    fun findByStatus(status: FactStatus): Facts = repo.findByStatus(status)

    fun findApprovedByService(serviceId: String): Facts =
        repo.findByServiceIdAndStatus(serviceId, FactStatus.APPROVED)

    fun searchApproved(
        query: String,
        serviceId: String?,
        topK: Int,
    ): Facts = index.search(query, serviceId, topK)

    fun create(proposal: FactProposal): Mono<Fact> {
        val fact =
            Fact(
                serviceId = proposal.serviceId,
                symptom = proposal.symptom,
                rootCause = proposal.rootCause,
                resolution = proposal.resolution,
                confidence = proposal.confidence,
                investigationId = proposal.investigationId,
            )
        return index
            .embedding(fact)
            .flatMap { embedding ->
                repo.save(fact.copy(embedding = embedding))
            }
    }

    @Transactional
    fun approve(
        id: String,
        approvedBy: String,
    ): Mono<Fact> {
        if (approvedBy.isBlank()) {
            return Mono.error(
                InvalidRequestException("approvedBy must not be blank"),
            )
        }

        val existing =
            repo
                .findById(id)
                .orNotFound("Fact", id)

        return existing.flatMap { fact ->
            if (fact.status != FactStatus.PROPOSED) {
                return@flatMap Mono.error(
                    InvalidFactStateException(
                        fact.status,
                        FactStatus.APPROVED,
                    ),
                )
            }

            withEmbedding(fact)
                .flatMap { embedded ->
                    repo.save(
                        embedded.copy(
                            status = FactStatus.APPROVED,
                            approvedBy = approvedBy,
                            approvedAt = Instant.now(),
                        ),
                    )
                }
        }
    }

    @Transactional
    fun reject(id: String): Mono<Fact> {
        val existing =
            repo
                .findById(id)
                .orNotFound("Fact", id)

        return existing.flatMap { fact ->
            if (fact.status != FactStatus.PROPOSED) {
                return@flatMap Mono.error(
                    InvalidFactStateException(
                        fact.status,
                        FactStatus.REJECTED,
                    ),
                )
            }

            repo.save(
                fact.copy(status = FactStatus.REJECTED),
            )
        }
    }

    @Transactional
    fun delete(id: String): Mono<Void> {
        val existing =
            repo
                .findById(id)
                .orNotFound("Fact", id)

        return existing.flatMap {
            repo.deleteById(id)
        }
    }

    private fun withEmbedding(fact: Fact): Mono<Fact> {
        if (fact.embedding.isNotEmpty()) {
            return Mono.just(fact)
        }
        return index
            .embedding(fact)
            .map { embedding -> fact.copy(embedding = embedding) }
    }
}
