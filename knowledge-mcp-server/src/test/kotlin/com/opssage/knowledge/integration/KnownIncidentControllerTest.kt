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

import com.opssage.knowledge.dto.CreateKnownIncidentRequest
import com.opssage.knowledge.dto.UpdateKnownIncidentRequest
import com.opssage.knowledge.model.KnownIncident
import com.opssage.knowledge.repository.KnownIncidentMongoRepository
import com.opssage.knowledge.testcontainers.TestContainersConfiguration
import com.opssage.knowledge.unit.fixture.KnownIncidentFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import java.time.Instant

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KnownIncidentControllerTest {

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
    lateinit var repository: KnownIncidentMongoRepository

    @BeforeEach
    fun cleanUp() {
        webTestClient =
            WebTestClient
                .bindToServer()
                .baseUrl("http://localhost:$port")
                .build()
        repository.deleteAll().block()
    }

    @Test
    fun `POST incident returns 201 and persists the incident`() {
        val request =
            CreateKnownIncidentRequest(
                serviceId = "payment-svc",
                title = "Payment latency spike",
                occurredAt = Instant.parse("2026-01-01T00:00:00Z"),
                symptoms = listOf("p99 latency above 2s"),
                rootCause = "Mongo connection pool exhausted",
                resolution = "Increased pool size",
                evidence = listOf("traceId=abc123"),
                relatedServices = listOf("cart-svc"),
            )

        val created =
            webTestClient
                .post()
                .uri("/api/v1/known-incidents")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isCreated
                .expectBody(KnownIncident::class.java)
                .returnResult()
                .responseBody!!

        assertThat(created.id).isNotNull()
        assertThat(created.title).isEqualTo(request.title)
        assertThat(created.serviceId).isEqualTo("payment-svc")
    }

    @Test
    fun `GET incident by id returns the stored incident`() {
        val saved =
            repository
                .save(KnownIncidentFixture.knownIncident(id = null))
                .block()!!

        webTestClient
            .get()
            .uri("/api/v1/known-incidents/${saved.id}")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.id")
            .isEqualTo(saved.id!!)
            .jsonPath("$.title")
            .isEqualTo(saved.title)
    }

    @Test
    fun `GET incident by id returns 404 when not found`() {
        webTestClient
            .get()
            .uri("/api/v1/known-incidents/nonexistent-id")
            .exchange()
            .expectStatus()
            .isNotFound
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("RESOURCE_NOT_FOUND")
    }

    @Test
    fun `GET incidents returns all when no serviceId filter`() {
        repository
            .saveAll(
                listOf(
                    KnownIncidentFixture.knownIncident(
                        id = null,
                        serviceId = "svc-a",
                    ),
                    KnownIncidentFixture.knownIncident(
                        id = null,
                        serviceId = "svc-b",
                    ),
                ),
            ).collectList()
            .block()

        webTestClient
            .get()
            .uri("/api/v1/known-incidents")
            .exchange()
            .expectStatus()
            .isOk
            .expectBodyList(KnownIncident::class.java)
            .hasSize(2)
    }

    @Test
    fun `GET incidents filters by serviceId`() {
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

        webTestClient
            .get()
            .uri("/api/v1/known-incidents?serviceId=payment-svc")
            .exchange()
            .expectStatus()
            .isOk
            .expectBodyList(KnownIncident::class.java)
            .hasSize(1)
    }

    @Test
    fun `PUT incident updates and returns the updated incident`() {
        val saved =
            repository
                .save(
                    KnownIncidentFixture.knownIncident(
                        id = null,
                        title = "Old Title",
                    ),
                ).block()!!
        val request =
            UpdateKnownIncidentRequest(
                serviceId = saved.serviceId,
                title = "Updated Title",
                occurredAt = saved.occurredAt,
                symptoms = listOf("updated symptom"),
                rootCause = "updated root cause",
            )

        webTestClient
            .put()
            .uri("/api/v1/known-incidents/${saved.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.title")
            .isEqualTo("Updated Title")
            .jsonPath("$.id")
            .isEqualTo(saved.id!!)
    }

    @Test
    fun `DELETE incident returns 204 and removes the incident`() {
        val saved =
            repository
                .save(KnownIncidentFixture.knownIncident(id = null))
                .block()!!

        webTestClient
            .delete()
            .uri("/api/v1/known-incidents/${saved.id}")
            .exchange()
            .expectStatus()
            .isNoContent

        val remaining = repository.findById(saved.id!!).block()
        assertThat(remaining).isNull()
    }

    @Test
    fun `POST incident returns 400 when required fields are blank`() {
        val request =
            CreateKnownIncidentRequest(
                serviceId = "  ",
                title = "  ",
                occurredAt = Instant.parse("2026-01-01T00:00:00Z"),
                symptoms = listOf("valid symptom"),
                rootCause = "  ",
            )

        webTestClient
            .post()
            .uri("/api/v1/known-incidents")
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
