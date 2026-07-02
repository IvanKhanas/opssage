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

import com.opssage.knowledge.repository.ServiceProfileMongoRepository
import com.opssage.knowledge.unit.fixture.ServiceProfileFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ServiceProfileRepositoryTest {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun mongoProps(registry: DynamicPropertyRegistry) =
            TestContainersConfiguration.configure(registry)
    }

    @Autowired
    lateinit var repository: ServiceProfileMongoRepository

    @BeforeEach
    fun cleanUp() {
        repository.deleteAll().block()
    }

    @Test
    fun `findByServiceId returns profile for the given serviceId`() {
        repository
            .saveAll(
                listOf(
                    ServiceProfileFixture.profile(
                        id = null,
                        serviceId = "payment-svc",
                    ),
                    ServiceProfileFixture.profile(
                        id = null,
                        serviceId = "auth-svc",
                    ),
                ),
            ).collectList()
            .block()

        val result = repository.findByServiceId("payment-svc").block()

        assertThat(result).isNotNull
        assertThat(result!!.serviceId).isEqualTo("payment-svc")
    }

    @Test
    fun `findByServiceId returns empty when no profile matches`() {
        repository
            .save(
                ServiceProfileFixture.profile(
                    id = null,
                    serviceId = "auth-svc",
                ),
            ).block()

        val result = repository.findByServiceId("nonexistent-svc").block()

        assertThat(result).isNull()
    }
}
