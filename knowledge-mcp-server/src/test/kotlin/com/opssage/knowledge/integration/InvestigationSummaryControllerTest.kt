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

import com.opssage.knowledge.dto.CreateInvestigationSummaryRequest
import com.opssage.knowledge.model.Confidence
import com.opssage.knowledge.model.InvestigationSummary
import com.opssage.knowledge.repository.InvestigationSummaryMongoRepository
import com.opssage.knowledge.testcontainers.TestContainersConfiguration
import com.opssage.knowledge.unit.fixture.InvestigationSummaryFixture
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
class InvestigationSummaryControllerTest {

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
    lateinit var repository: InvestigationSummaryMongoRepository

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
    fun `POST summary returns 201 and persists it`() {
        val request =
            CreateInvestigationSummaryRequest(
                investigationId = "inv-1",
                serviceId = "payment-svc",
                summary = "Latency spike traced to slow Mongo query",
                mostLikelyCause = "Missing index on orders collection",
                confidence = Confidence.HIGH,
                evidence = listOf("p99 grew from 40ms to 900ms"),
                recommendedActions = listOf("Add index on orders.userId"),
            )

        val created =
            webTestClient
                .post()
                .uri("/api/v1/investigation-summaries")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isCreated
                .expectBody(InvestigationSummary::class.java)
                .returnResult()
                .responseBody!!

        assertThat(created.id).isNotNull()
        assertThat(created.investigationId).isEqualTo("inv-1")
        assertThat(created.confidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun `GET summaries filters by service`() {
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

        webTestClient
            .get()
            .uri("/api/v1/investigation-summaries?serviceId=payment-svc")
            .exchange()
            .expectStatus()
            .isOk
            .expectBodyList(InvestigationSummary::class.java)
            .hasSize(1)
    }

    @Test
    fun `GET summaries filters by investigation`() {
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

        webTestClient
            .get()
            .uri("/api/v1/investigation-summaries?investigationId=inv-1")
            .exchange()
            .expectStatus()
            .isOk
            .expectBodyList(InvestigationSummary::class.java)
            .hasSize(1)
    }

    @Test
    fun `GET summary by id returns 404 when not found`() {
        webTestClient
            .get()
            .uri("/api/v1/investigation-summaries/nonexistent-id")
            .exchange()
            .expectStatus()
            .isNotFound
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("RESOURCE_NOT_FOUND")
    }

    @Test
    fun `DELETE summary returns 204 and removes it`() {
        val saved =
            repository
                .save(
                    InvestigationSummaryFixture.investigationSummary(id = null),
                ).block()!!

        webTestClient
            .delete()
            .uri("/api/v1/investigation-summaries/${saved.id}")
            .exchange()
            .expectStatus()
            .isNoContent

        val remaining = repository.findById(saved.id!!).block()
        assertThat(remaining).isNull()
    }

    @Test
    fun `POST summary returns 400 when required fields are blank`() {
        val request =
            CreateInvestigationSummaryRequest(
                investigationId = "  ",
                serviceId = "  ",
                summary = "  ",
                mostLikelyCause = "  ",
            )

        webTestClient
            .post()
            .uri("/api/v1/investigation-summaries")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isBadRequest
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("VALIDATION_ERROR")
    }

    @Test
    fun `POST summary applies defaults when optional fields are omitted`() {
        val partialBody =
            """
            {
              "investigationId": "inv-partial",
              "serviceId": "payment-svc",
              "summary": "latency spike resolved",
              "mostLikelyCause": "missing index"
            }
            """.trimIndent()

        webTestClient
            .post()
            .uri("/api/v1/investigation-summaries")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(partialBody)
            .exchange()
            .expectStatus()
            .isCreated
            .expectBody()
            .jsonPath("$.confidence")
            .isEqualTo("MEDIUM")
            .jsonPath("$.evidence")
            .isEmpty
    }
}
