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

import com.opssage.knowledge.repository.RunbookMongoRepository
import com.opssage.knowledge.unit.fixture.RunbookFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RunbookRepositoryTest {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun mongoProps(registry: DynamicPropertyRegistry) =
            TestContainersConfiguration.configure(registry)
    }

    @Autowired
    lateinit var repository: RunbookMongoRepository

    @BeforeEach
    fun cleanUp() {
        repository.deleteAll().block()
    }

    @Test
    fun `findByServiceId returns only runbooks for the given service`() {
        repository
            .saveAll(
                listOf(
                    RunbookFixture.runbook(
                        id = null,
                        serviceId = "payment-svc",
                    ),
                    RunbookFixture.runbook(id = null, serviceId = "auth-svc"),
                ),
            ).collectList()
            .block()

        val result =
            repository
                .findByServiceId(
                    "payment-svc",
                ).collectList()
                .block()!!

        assertThat(result).hasSize(1)
        assertThat(result.first().serviceId).isEqualTo("payment-svc")
    }

    @Test
    fun `findByAlertName returns runbooks matching the alert name`() {
        repository
            .saveAll(
                listOf(
                    RunbookFixture.runbook(
                        id = null,
                        alertName = "HighLatency",
                    ),
                    RunbookFixture.runbook(id = null, alertName = "DiskFull"),
                ),
            ).collectList()
            .block()

        val result =
            repository
                .findByAlertName(
                    "HighLatency",
                ).collectList()
                .block()!!

        assertThat(result).hasSize(1)
        assertThat(result.first().alertName).isEqualTo("HighLatency")
    }

    @Test
    fun `findByAlertName returns empty when no runbook matches`() {
        repository
            .save(
                RunbookFixture.runbook(id = null, alertName = "DiskFull"),
            ).block()

        val result =
            repository
                .findByAlertName(
                    "nonexistent-alert",
                ).collectList()
                .block()!!

        assertThat(result).isEmpty()
    }
}
