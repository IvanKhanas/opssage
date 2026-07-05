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
import io.github.oshai.kotlinlogging.KotlinLogging
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

    private val log = KotlinLogging.logger {}

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
            .next()
            .flatMap { existing -> reconcileExisting(existing) }
            .switchIfEmpty(createIndex())
            .then()

    private fun reconcileExisting(existing: Document): Mono<Boolean> {
        if (matchesDesired(existing)) {
            return Mono.just(true)
        }
        log.atWarn {
            message = "Atlas vector index definition drifted, updating"
            payload =
                mapOf(
                    "index" to properties.indexName,
                    "dimensions" to properties.dimensions,
                )
        }
        return mongo.executeCommand(updateIndexCommand()).thenReturn(true)
    }

    private fun createIndex(): Mono<Boolean> =
        Mono.defer {
            log.atInfo {
                message = "Creating Atlas vector index"
                payload =
                    mapOf(
                        "index" to properties.indexName,
                        "dimensions" to properties.dimensions,
                    )
            }
            mongo.executeCommand(createIndexCommand()).thenReturn(true)
        }

    @Suppress("UNCHECKED_CAST")
    private fun matchesDesired(existing: Document): Boolean {
        val definition =
            existing.get(LATEST_DEFINITION, Document::class.java)
                ?: return false
        val fields =
            definition.get(FIELDS, List::class.java) as? List<Document>
                ?: return false
        val vector = fields.firstOrNull { it.getString(TYPE) == TYPE_VECTOR }
        val vectorOk =
            vector != null &&
                vector.getString(PATH) == EMBEDDING_PATH &&
                vector.getInteger(NUM_DIMENSIONS) == properties.dimensions &&
                vector.getString(SIMILARITY) == SIMILARITY_COSINE
        val filterPaths =
            fields
                .filter { it.getString(TYPE) == TYPE_FILTER }
                .mapNotNull { it.getString(PATH) }
                .toSet()
        return vectorOk && filterPaths == DESIRED_FILTER_PATHS
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
                        .append(TYPE, INDEX_TYPE_VECTOR_SEARCH)
                        .append("definition", indexDefinition()),
                ),
            )

    private fun updateIndexCommand(): Document =
        Document("updateSearchIndex", FACTS_COLLECTION)
            .append("name", properties.indexName)
            .append("definition", indexDefinition())

    private fun indexDefinition(): Document =
        Document(
            FIELDS,
            listOf(vectorField()) +
                DESIRED_FILTER_PATHS.map { path -> filterField(path) },
        )

    private fun vectorField(): Document =
        Document(TYPE, TYPE_VECTOR)
            .append(PATH, EMBEDDING_PATH)
            .append(NUM_DIMENSIONS, properties.dimensions)
            .append(SIMILARITY, SIMILARITY_COSINE)

    private fun filterField(path: String): Document =
        Document(TYPE, TYPE_FILTER).append(PATH, path)

    private companion object {
        const val FACTS_COLLECTION = "facts"
        const val STATUS_FIELD = "status"
        const val READY_STATUS = "READY"
        const val LATEST_DEFINITION = "latestDefinition"
        const val FIELDS = "fields"
        const val TYPE = "type"
        const val PATH = "path"
        const val TYPE_VECTOR = "vector"
        const val TYPE_FILTER = "filter"
        const val NUM_DIMENSIONS = "numDimensions"
        const val SIMILARITY = "similarity"
        const val SIMILARITY_COSINE = "cosine"
        const val EMBEDDING_PATH = "embedding"
        const val INDEX_TYPE_VECTOR_SEARCH = "vectorSearch"
        val DESIRED_FILTER_PATHS = setOf("status", "serviceId")
        val POLL_INTERVAL: Duration = Duration.ofSeconds(1)
        val STARTUP_TIMEOUT: Duration = Duration.ofMinutes(2)
    }
}
