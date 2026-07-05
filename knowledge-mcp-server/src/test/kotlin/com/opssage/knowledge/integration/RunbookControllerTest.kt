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

import com.opssage.knowledge.dto.CreateRunbookRequest
import com.opssage.knowledge.dto.UpdateRunbookRequest
import com.opssage.knowledge.model.Runbook
import com.opssage.knowledge.repository.RunbookMongoRepository
import com.opssage.knowledge.testcontainers.TestContainersConfiguration
import com.opssage.knowledge.unit.fixture.RunbookFixture
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
class RunbookControllerTest {

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
    lateinit var repository: RunbookMongoRepository

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
    fun `POST runbook returns 201 and persists the runbook`() {
        val request =
            CreateRunbookRequest(
                serviceId = "payment-svc",
                title = "High Latency Runbook",
                triggerType = "ALERT",
                description = "Steps to resolve high latency",
                symptoms = listOf("p99 latency above 1s"),
                steps =
                    listOf(
                        "Check metrics dashboard",
                        "Restart payment pods",
                    ),
                recommendedTools = listOf("get_service_health"),
                dangerNotes = listOf("Do not restart during settlement"),
            )

        val created =
            webTestClient
                .post()
                .uri("/api/v1/runbooks")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isCreated
                .expectBody(Runbook::class.java)
                .returnResult()
                .responseBody!!

        assertThat(created.id).isNotNull()
        assertThat(created.title).isEqualTo(request.title)
        assertThat(created.serviceId).isEqualTo("payment-svc")
        assertThat(created.triggerType).isEqualTo("ALERT")
        assertThat(created.symptoms).containsExactly("p99 latency above 1s")
        assertThat(created.dangerNotes)
            .containsExactly("Do not restart during settlement")
    }

    @Test
    fun `GET runbook by id returns the stored runbook`() {
        val saved = repository.save(RunbookFixture.runbook(id = null)).block()!!

        webTestClient
            .get()
            .uri("/api/v1/runbooks/${saved.id}")
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
    fun `GET runbook by id returns 404 when not found`() {
        webTestClient
            .get()
            .uri("/api/v1/runbooks/nonexistent-id")
            .exchange()
            .expectStatus()
            .isNotFound
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("RESOURCE_NOT_FOUND")
    }

    @Test
    fun `GET runbooks returns all when no serviceId filter`() {
        repository
            .saveAll(
                listOf(
                    RunbookFixture.runbook(id = null, serviceId = "svc-a"),
                    RunbookFixture.runbook(id = null, serviceId = "svc-b"),
                ),
            ).collectList()
            .block()

        webTestClient
            .get()
            .uri("/api/v1/runbooks")
            .exchange()
            .expectStatus()
            .isOk
            .expectBodyList(Runbook::class.java)
            .hasSize(2)
    }

    @Test
    fun `GET runbooks filters by serviceId`() {
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

        webTestClient
            .get()
            .uri("/api/v1/runbooks?serviceId=payment-svc")
            .exchange()
            .expectStatus()
            .isOk
            .expectBodyList(Runbook::class.java)
            .hasSize(1)
    }

    @Test
    fun `PUT runbook updates and returns the updated runbook`() {
        val saved =
            repository
                .save(
                    RunbookFixture.runbook(id = null, title = "Old Title"),
                ).block()!!
        val request =
            UpdateRunbookRequest(
                serviceId = saved.serviceId,
                title = "Updated Title",
                description = "Updated description for runbook",
                steps = listOf("New step 1", "New step 2"),
            )

        webTestClient
            .put()
            .uri("/api/v1/runbooks/${saved.id}")
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
    fun `DELETE runbook returns 204 and removes the runbook`() {
        val saved = repository.save(RunbookFixture.runbook(id = null)).block()!!

        webTestClient
            .delete()
            .uri("/api/v1/runbooks/${saved.id}")
            .exchange()
            .expectStatus()
            .isNoContent

        val remaining = repository.findById(saved.id!!).block()
        assertThat(remaining).isNull()
    }

    @Test
    fun `POST runbook returns 400 when required fields are blank`() {
        val request =
            CreateRunbookRequest(
                serviceId = "  ",
                title = "  ",
                description = "  ",
                steps = listOf("valid-step"),
            )

        webTestClient
            .post()
            .uri("/api/v1/runbooks")
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
