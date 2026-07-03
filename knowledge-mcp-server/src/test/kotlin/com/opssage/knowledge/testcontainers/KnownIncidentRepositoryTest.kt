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

import com.opssage.knowledge.repository.KnownIncidentMongoRepository
import com.opssage.knowledge.unit.fixture.KnownIncidentFixture
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
class KnownIncidentRepositoryTest {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun mongoProps(registry: DynamicPropertyRegistry) =
            TestContainersConfiguration.configure(registry)
    }

    @Autowired
    lateinit var repository: KnownIncidentMongoRepository

    @BeforeEach
    fun cleanUp() {
        repository.deleteAll().block()
    }

    @Test
    fun `findByServiceId returns only incidents for the given service`() {
        repository
            .saveAll(
                listOf(
                    KnownIncidentFixture.knownIncident(
                        id = null,
                        serviceId = "payment-svc",
                    ),
                    KnownIncidentFixture.knownIncident(
                        id = null,
                        serviceId = "auth-svc",
                    ),
                ),
            ).collectList()
            .block()

        val result =
            repository.findByServiceId("payment-svc").collectList().block()!!

        assertThat(result).hasSize(1)
        assertThat(result.first().serviceId).isEqualTo("payment-svc")
    }

    @Test
    fun `findByTitleContainingIgnoreCase matches regardless of case`() {
        repository
            .saveAll(
                listOf(
                    KnownIncidentFixture.knownIncident(
                        id = null,
                        title = "Payment OOM crash",
                    ),
                    KnownIncidentFixture.knownIncident(
                        id = null,
                        title = "Disk full",
                    ),
                ),
            ).collectList()
            .block()

        val result =
            repository
                .findByTitleContainingIgnoreCase("oom")
                .collectList()
                .block()!!

        assertThat(result).hasSize(1)
        assertThat(result.first().title).contains("OOM")
    }

    @Test
    fun `findByRelatedServicesContains returns incidents for the service`() {
        repository
            .saveAll(
                listOf(
                    KnownIncidentFixture.knownIncident(
                        id = null,
                        relatedServices = listOf("auth-svc", "cart-svc"),
                    ),
                    KnownIncidentFixture.knownIncident(
                        id = null,
                        relatedServices = listOf("cart-svc"),
                    ),
                ),
            ).collectList()
            .block()

        val result =
            repository
                .findByRelatedServicesContains("auth-svc")
                .collectList()
                .block()!!

        assertThat(result).hasSize(1)
        assertThat(result.first().relatedServices).contains("auth-svc")
    }

    @ParameterizedTest
    @ValueSource(strings = ["latency", "timeout", "oom"])
    fun `findByTitleContainingIgnoreCase returns incidents matching keyword`(
        keyword: String,
    ) {
        repository
            .saveAll(
                listOf(
                    KnownIncidentFixture.knownIncident(
                        id = null,
                        title = "High $keyword in checkout",
                    ),
                    KnownIncidentFixture.knownIncident(
                        id = null,
                        title = "Unrelated incident",
                    ),
                ),
            ).collectList()
            .block()

        val result =
            repository
                .findByTitleContainingIgnoreCase(keyword)
                .collectList()
                .block()!!

        assertThat(result).hasSize(1)
        assertThat(result.first().title).contains(keyword)
    }
}
