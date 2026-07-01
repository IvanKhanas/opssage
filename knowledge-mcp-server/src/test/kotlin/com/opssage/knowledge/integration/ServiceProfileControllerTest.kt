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
package com.opssage.knowledge.integration

import com.opssage.knowledge.dto.CreateServiceProfileRequest
import com.opssage.knowledge.dto.UpdateServiceProfileRequest
import com.opssage.knowledge.model.Criticality
import com.opssage.knowledge.model.ServiceProfile
import com.opssage.knowledge.repository.ServiceProfileMongoRepository
import com.opssage.knowledge.testcontainers.TestContainersConfiguration
import com.opssage.knowledge.unit.fixture.ServiceProfileFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServiceProfileControllerTest {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun mongoProps(registry: DynamicPropertyRegistry) =
            TestContainersConfiguration.configure(registry)
    }

    @LocalServerPort
    var port: Int = 0

    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var repository: ServiceProfileMongoRepository

    @BeforeEach
    fun cleanUp() {
        webTestClient =
            WebTestClient
                .bindToServer()
                .baseUrl(
                    "http://localhost:$port",
                ).build()
        repository.deleteAll().block()
    }

    @Test
    fun `POST service profile returns 201 and persists the profile`() {
        val request =
            CreateServiceProfileRequest(
                serviceId = "auth-svc",
                displayName = "Auth Service",
                description = "Handles authentication and authorization",
                team = "platform",
                criticality = Criticality.HIGH,
                dependencies = listOf("user-db"),
            )

        val created =
            webTestClient
                .post()
                .uri("/api/v1/service-profiles")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isCreated
                .expectBody(ServiceProfile::class.java)
                .returnResult()
                .responseBody!!

        assertThat(created.id).isNotNull()
        assertThat(created.serviceId).isEqualTo("auth-svc")
        assertThat(created.displayName).isEqualTo("Auth Service")
    }

    @Test
    fun `GET service profile by id returns the stored profile`() {
        val saved =
            repository
                .save(
                    ServiceProfileFixture.profile(id = null),
                ).block()!!

        webTestClient
            .get()
            .uri("/api/v1/service-profiles/${saved.id}")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.id")
            .isEqualTo(saved.id!!)
            .jsonPath("$.displayName")
            .isEqualTo(saved.displayName)
    }

    @Test
    fun `GET service profile by id returns 404 when not found`() {
        webTestClient
            .get()
            .uri("/api/v1/service-profiles/nonexistent-id")
            .exchange()
            .expectStatus()
            .isNotFound
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("RESOURCE_NOT_FOUND")
    }

    @Test
    fun `GET service profiles returns all profiles`() {
        repository
            .saveAll(
                listOf(
                    ServiceProfileFixture.profile(
                        id = null,
                        serviceId = "svc-a",
                    ),
                    ServiceProfileFixture.profile(
                        id = null,
                        serviceId = "svc-b",
                    ),
                ),
            ).collectList()
            .block()

        webTestClient
            .get()
            .uri("/api/v1/service-profiles")
            .exchange()
            .expectStatus()
            .isOk
            .expectBodyList(ServiceProfile::class.java)
            .hasSize(2)
    }

    @Test
    fun `GET by serviceId returns the matching profile`() {
        repository
            .save(
                ServiceProfileFixture.profile(
                    id = null,
                    serviceId = "payment-svc",
                ),
            ).block()!!

        webTestClient
            .get()
            .uri("/api/v1/service-profiles/by-service/payment-svc")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.serviceId")
            .isEqualTo("payment-svc")
    }

    @Test
    fun `PUT service profile updates and returns the updated profile`() {
        val saved =
            repository
                .save(
                    ServiceProfileFixture.profile(
                        id = null,
                        displayName = "Old Name",
                    ),
                ).block()!!
        val request =
            UpdateServiceProfileRequest(
                serviceId = saved.serviceId,
                displayName = "Updated Name",
                description = "Updated description",
                team = "sre",
                criticality = Criticality.MEDIUM,
            )

        webTestClient
            .put()
            .uri("/api/v1/service-profiles/${saved.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.displayName")
            .isEqualTo("Updated Name")
            .jsonPath("$.id")
            .isEqualTo(saved.id!!)
    }

    @Test
    fun `DELETE service profile returns 204 and removes the profile`() {
        val saved =
            repository
                .save(
                    ServiceProfileFixture.profile(id = null),
                ).block()!!

        webTestClient
            .delete()
            .uri("/api/v1/service-profiles/${saved.id}")
            .exchange()
            .expectStatus()
            .isNoContent

        val remaining = repository.findById(saved.id!!).block()
        assertThat(remaining).isNull()
    }

    @Test
    fun `POST service profile returns 400 when required fields are blank`() {
        val request =
            CreateServiceProfileRequest(
                serviceId = "  ",
                displayName = "  ",
                description = "  ",
                team = "  ",
                criticality = Criticality.LOW,
            )

        webTestClient
            .post()
            .uri("/api/v1/service-profiles")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isBadRequest
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("VALIDATION_ERROR")
    }
}
