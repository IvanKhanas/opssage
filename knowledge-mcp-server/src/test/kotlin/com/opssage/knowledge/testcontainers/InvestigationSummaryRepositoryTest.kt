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

import com.opssage.knowledge.repository.InvestigationSummaryMongoRepository
import com.opssage.knowledge.unit.fixture.InvestigationSummaryFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InvestigationSummaryRepositoryTest {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun mongoProps(registry: DynamicPropertyRegistry) =
            TestContainersConfiguration.configure(registry)
    }

    @Autowired
    lateinit var repository: InvestigationSummaryMongoRepository

    @BeforeEach
    fun cleanUp() {
        repository.deleteAll().block()
    }

    @Test
    fun `findByServiceId returns only summaries for the service`() {
        repository
            .saveAll(
                listOf(
                    InvestigationSummaryFixture.investigationSummary(
                        id = null,
                        serviceId = "payment-svc",
                    ),
                    InvestigationSummaryFixture.investigationSummary(
                        id = null,
                        serviceId = "orders-svc",
                    ),
                ),
            ).collectList()
            .block()

        val result =
            repository
                .findByServiceId("payment-svc")
                .collectList()
                .block()!!

        assertThat(result).hasSize(1)
        assertThat(result.first().serviceId).isEqualTo("payment-svc")
    }

    @Test
    fun `findByInvestigationId returns summaries for the investigation`() {
        repository
            .saveAll(
                listOf(
                    InvestigationSummaryFixture.investigationSummary(
                        id = null,
                        investigationId = "inv-1",
                    ),
                    InvestigationSummaryFixture.investigationSummary(
                        id = null,
                        investigationId = "inv-2",
                    ),
                ),
            ).collectList()
            .block()

        val result =
            repository
                .findByInvestigationId("inv-1")
                .collectList()
                .block()!!

        assertThat(result).hasSize(1)
        assertThat(result.first().investigationId).isEqualTo("inv-1")
    }
}
