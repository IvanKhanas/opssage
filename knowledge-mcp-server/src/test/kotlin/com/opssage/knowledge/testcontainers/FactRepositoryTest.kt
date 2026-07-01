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

import com.opssage.knowledge.model.FactStatus
import com.opssage.knowledge.repository.FactMongoRepository
import com.opssage.knowledge.unit.fixture.FactFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FactRepositoryTest {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun mongoProps(registry: DynamicPropertyRegistry) =
            TestContainersConfiguration.configure(registry)
    }

    @Autowired
    lateinit var repository: FactMongoRepository

    @BeforeEach
    fun cleanUp() {
        repository.deleteAll().block()
    }

    @Test
    fun `findByStatus returns only facts with the given status`() {
        repository
            .saveAll(
                listOf(
                    FactFixture.fact(id = null, status = FactStatus.APPROVED),
                    FactFixture.fact(id = null, status = FactStatus.PROPOSED),
                ),
            ).collectList()
            .block()

        val result =
            repository
                .findByStatus(
                    FactStatus.APPROVED,
                ).collectList()
                .block()!!

        assertThat(result).hasSize(1)
        assertThat(result.first().status).isEqualTo(FactStatus.APPROVED)
    }

    @Test
    fun `findByServiceIdAndStatus returns only matching service and status`() {
        repository
            .saveAll(
                listOf(
                    FactFixture.fact(
                        id = null,
                        serviceId = "payment-svc",
                        status = FactStatus.APPROVED,
                    ),
                    FactFixture.fact(
                        id = null,
                        serviceId = "auth-svc",
                        status = FactStatus.APPROVED,
                    ),
                ),
            ).collectList()
            .block()

        val result =
            repository
                .findByServiceIdAndStatus("payment-svc", FactStatus.APPROVED)
                .collectList()
                .block()!!

        assertThat(result).hasSize(1)
        assertThat(result.first().serviceId).isEqualTo("payment-svc")
    }

    @Test
    fun `findByTagsContainsAndStatus filters by tag and status`() {
        repository
            .saveAll(
                listOf(
                    FactFixture.fact(
                        id = null,
                        tags = listOf("mongodb", "latency"),
                        status = FactStatus.APPROVED,
                    ),
                    FactFixture.fact(
                        id = null,
                        tags = listOf("redis"),
                        status = FactStatus.APPROVED,
                    ),
                ),
            ).collectList()
            .block()

        val result =
            repository
                .findByTagsContainsAndStatus("mongodb", FactStatus.APPROVED)
                .collectList()
                .block()!!

        assertThat(result).hasSize(1)
        assertThat(result.first().tags).contains("mongodb")
    }

    @ParameterizedTest
    @ValueSource(strings = ["TIMEOUT", "Timeout", "timeout"])
    fun `findBySymptomContainingIgnoreCaseAndStatus is case-insensitive`(
        keyword: String,
    ) {
        repository
            .save(
                FactFixture.fact(
                    id = null,
                    symptom = "Connection timeout after 30s",
                    status = FactStatus.APPROVED,
                ),
            ).block()

        val result =
            repository
                .findBySymptomContainingIgnoreCaseAndStatus(
                    keyword,
                    FactStatus.APPROVED,
                ).collectList()
                .block()!!

        assertThat(result).hasSize(1)
    }

    @Test
    fun `symptom search excludes non-approved facts`() {
        repository
            .save(
                FactFixture.fact(
                    id = null,
                    symptom = "high latency spike",
                    status = FactStatus.PROPOSED,
                ),
            ).block()

        val result =
            repository
                .findBySymptomContainingIgnoreCaseAndStatus(
                    "latency",
                    FactStatus.APPROVED,
                ).collectList()
                .block()!!

        assertThat(result).isEmpty()
    }
}
