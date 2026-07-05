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

import com.opssage.knowledge.config.VectorSearchProperties
import com.opssage.knowledge.model.Fact
import com.opssage.knowledge.model.FactStatus
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.data.domain.Vector
import org.springframework.data.mongodb.core.ReactiveMongoOperations
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.VectorSearchOperation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Repository

@Repository
class AtlasFactVectorIndex(
    private val mongo: ReactiveMongoOperations,
    private val model: EmbeddingModel,
    private val properties: VectorSearchProperties,
) : FactVectorIndex {

    override fun embedding(fact: Fact): Mono<Vector> =
        embed("${fact.symptom}\n${fact.rootCause}")

    override fun search(
        query: String,
        serviceId: String?,
        topK: Int,
    ): Flux<Fact> =
        embed(query)
            .flatMapMany { vector ->
                mongo.aggregate(
                    Aggregation.newAggregation(
                        searchOperation(vector, serviceId, topK),
                    ),
                    Fact::class.java,
                    Fact::class.java,
                )
            }

    private fun embed(text: String): Mono<Vector> =
        Mono
            .fromCallable { Vector.of(*model.embed(text)) }
            .subscribeOn(Schedulers.boundedElastic())

    private fun searchOperation(
        vector: Vector,
        serviceId: String?,
        topK: Int,
    ): VectorSearchOperation {
        val candidates =
            (topK * properties.candidatesMultiplier)
                .coerceIn(topK, MAX_CANDIDATES)
        return VectorSearchOperation
            .search(properties.indexName)
            .path(EMBEDDING_PATH)
            .vector(vector)
            .limit(topK)
            .numCandidates(candidates)
            .filter(searchFilter(serviceId))
    }

    private fun searchFilter(serviceId: String?): Criteria {
        val filter = Criteria.where(STATUS_PATH).`is`(FactStatus.APPROVED)
        if (serviceId != null) {
            filter.and(SERVICE_ID_PATH).`is`(serviceId)
        }
        return filter
    }

    private companion object {
        const val EMBEDDING_PATH = "embedding"
        const val STATUS_PATH = "status"
        const val SERVICE_ID_PATH = "serviceId"
        const val MAX_CANDIDATES = 10_000
    }
}
