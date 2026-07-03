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
package com.opssage.knowledge.testcontainers

import com.opssage.knowledge.model.Fact
import com.opssage.knowledge.model.FactStatus
import com.opssage.knowledge.repository.FactMongoRepository
import com.opssage.knowledge.repository.FactVectorIndex
import com.opssage.knowledge.unit.fixture.FactFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono

import java.time.Duration

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FactVectorIndexTest {

    companion object {
        private const val RESULT_LIMIT = 3
        private const val MAX_ATTEMPTS = 40L
        private val POLL_INTERVAL: Duration = Duration.ofMillis(500)
        private val SEARCH_TIMEOUT: Duration = Duration.ofSeconds(30)

        @JvmStatic
        @DynamicPropertySource
        fun mongoProps(registry: DynamicPropertyRegistry) =
            TestContainersConfiguration.configure(registry)
    }

    @Autowired
    lateinit var repository: FactMongoRepository

    @Autowired
    lateinit var index: FactVectorIndex

    @BeforeEach
    fun cleanUp() {
        repository.deleteAll().block()
    }

    @Test
    fun `semantic search returns only approved facts for requested service`() {
        val expected =
            embedded(
                FactFixture.fact(
                    id = null,
                    serviceId = "payment-svc",
                    symptom = "Mongo connection timeout",
                    rootCause = "Connection pool exhausted",
                    status = FactStatus.APPROVED,
                ),
            )
        val unrelated =
            embedded(
                FactFixture.fact(
                    id = null,
                    serviceId = "payment-svc",
                    symptom = "Pod CPU saturation",
                    rootCause = "Expensive encryption loop",
                    status = FactStatus.APPROVED,
                ),
            )
        val proposed =
            embedded(
                FactFixture.fact(
                    id = null,
                    serviceId = "payment-svc",
                    symptom = "Database connection timeout",
                    rootCause = "Connection pool exhausted",
                    status = FactStatus.PROPOSED,
                ),
            )
        val otherService =
            embedded(
                FactFixture.fact(
                    id = null,
                    serviceId = "auth-svc",
                    symptom = "Mongo connection timeout",
                    rootCause = "Connection pool exhausted",
                    status = FactStatus.APPROVED,
                ),
            )
        val saved =
            repository
                .saveAll(
                    listOf(
                        expected,
                        unrelated,
                        proposed,
                        otherService,
                    ),
                ).collectList()
                .block()!!
        val expectedId = saved.first().id

        val result =
            awaitResults(
                "database connection failure",
                "payment-svc",
            )

        assertThat(result.first().id).isEqualTo(expectedId)
        assertThat(result)
            .allMatch { fact -> fact.status == FactStatus.APPROVED }
            .allMatch { fact -> fact.serviceId == "payment-svc" }
    }

    private fun embedded(fact: Fact): Fact =
        fact.copy(embedding = index.embedding(fact).block()!!)

    private fun awaitResults(
        query: String,
        serviceId: String,
    ): List<Fact> =
        Mono
            .defer {
                index.search(query, serviceId, RESULT_LIMIT).collectList()
            }.filter { facts -> facts.isNotEmpty() }
            .repeatWhenEmpty { attempts ->
                attempts.delayElements(POLL_INTERVAL).take(MAX_ATTEMPTS)
            }.block(SEARCH_TIMEOUT)!!
}
