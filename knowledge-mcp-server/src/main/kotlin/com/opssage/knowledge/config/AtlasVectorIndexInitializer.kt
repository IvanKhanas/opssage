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
package com.opssage.knowledge.config

import com.opssage.knowledge.model.Fact
import org.bson.Document
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import java.time.Duration

import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.data.mongodb.core.ReactiveMongoOperations
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.AggregationOperation
import org.springframework.stereotype.Component

@Component
class AtlasVectorIndexInitializer(
    private val mongo: ReactiveMongoOperations,
    private val model: EmbeddingModel,
    private val properties: VectorSearchProperties,
) {

    @Order(0)
    @EventListener(ApplicationReadyEvent::class)
    fun initialize() {
        validateDimensions()
        ensureCollection()
            .then(ensureIndex())
            .then(waitUntilReady())
            .block(STARTUP_TIMEOUT)
    }

    private fun validateDimensions() {
        val actual = model.dimensions()
        check(actual == properties.dimensions) {
            "Embedding model has $actual dimensions, expected " +
                properties.dimensions
        }
    }

    private fun ensureCollection(): Mono<Void> =
        mongo
            .collectionExists(Fact::class.java)
            .flatMap { exists ->
                if (exists) {
                    Mono.empty()
                } else {
                    mongo.createCollection(Fact::class.java).then()
                }
            }

    private fun ensureIndex(): Mono<Void> =
        findIndex()
            .hasElements()
            .flatMap { exists ->
                if (exists) {
                    Mono.empty()
                } else {
                    mongo.executeCommand(createIndexCommand()).then()
                }
            }

    private fun waitUntilReady(): Mono<Void> =
        Flux
            .interval(Duration.ZERO, POLL_INTERVAL)
            .concatMap { indexReady() }
            .filter { ready -> ready }
            .next()
            .timeout(STARTUP_TIMEOUT)
            .then()

    private fun indexReady(): Mono<Boolean> =
        findIndex()
            .next()
            .map { index -> index.getString(STATUS_FIELD) == READY_STATUS }
            .defaultIfEmpty(false)

    private fun findIndex(): Flux<Document> {
        val operation =
            AggregationOperation {
                Document(
                    "\$listSearchIndexes",
                    Document("name", properties.indexName),
                )
            }
        return mongo.aggregate(
            Aggregation.newAggregation(operation),
            FACTS_COLLECTION,
            Document::class.java,
        )
    }

    private fun createIndexCommand(): Document =
        Document("createSearchIndexes", FACTS_COLLECTION)
            .append(
                "indexes",
                listOf(
                    Document("name", properties.indexName)
                        .append("type", "vectorSearch")
                        .append("definition", indexDefinition()),
                ),
            )

    private fun indexDefinition(): Document =
        Document(
            "fields",
            listOf(
                vectorField(),
                filterField("status"),
                filterField("serviceId"),
            ),
        )

    private fun vectorField(): Document =
        Document("type", "vector")
            .append("path", "embedding")
            .append("numDimensions", properties.dimensions)
            .append("similarity", "cosine")

    private fun filterField(path: String): Document =
        Document("type", "filter").append("path", path)

    private companion object {
        const val FACTS_COLLECTION = "facts"
        const val STATUS_FIELD = "status"
        const val READY_STATUS = "READY"
        val POLL_INTERVAL: Duration = Duration.ofSeconds(1)
        val STARTUP_TIMEOUT: Duration = Duration.ofMinutes(2)
    }
}
