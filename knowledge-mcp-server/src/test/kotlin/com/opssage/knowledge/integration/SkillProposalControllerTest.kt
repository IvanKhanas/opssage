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

import com.opssage.knowledge.dto.CreateSkillProposalRequest
import com.opssage.knowledge.model.SkillProposal
import com.opssage.knowledge.model.SkillProposalStatus
import com.opssage.knowledge.repository.SkillProposalMongoRepository
import com.opssage.knowledge.testcontainers.TestContainersConfiguration
import com.opssage.knowledge.unit.fixture.SkillProposalFixture
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
class SkillProposalControllerTest {

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
    lateinit var repository: SkillProposalMongoRepository

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
    fun `POST proposal returns 201 and persists a PROPOSED proposal`() {
        val request =
            CreateSkillProposalRequest(
                title = "Detect slow Mongo queries",
                problem = "No tool surfaces slow Mongo queries per service",
                proposedToolName = "get_slow_mongo_queries",
                expectedInputs = listOf("serviceId"),
                expectedOutputs = listOf("query", "p99"),
                motivation = "Would speed up latency investigations",
                examples = listOf("payment-svc latency spike"),
            )

        val created =
            webTestClient
                .post()
                .uri("/api/v1/skill-proposals")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isCreated
                .expectBody(SkillProposal::class.java)
                .returnResult()
                .responseBody!!

        assertThat(created.id).isNotNull()
        assertThat(created.status).isEqualTo(SkillProposalStatus.PROPOSED)
        assertThat(created.proposedToolName).isEqualTo("get_slow_mongo_queries")
    }

    @Test
    fun `GET proposals returns proposed ones by default`() {
        repository
            .saveAll(
                listOf(
                    SkillProposalFixture.skillProposal(
                        id = null,
                        status = SkillProposalStatus.PROPOSED,
                    ),
                    SkillProposalFixture.skillProposal(
                        id = null,
                        status = SkillProposalStatus.APPROVED,
                    ),
                ),
            ).collectList()
            .block()

        webTestClient
            .get()
            .uri("/api/v1/skill-proposals")
            .exchange()
            .expectStatus()
            .isOk
            .expectBodyList(SkillProposal::class.java)
            .hasSize(1)
    }

    @Test
    fun `GET proposal by id returns 404 when not found`() {
        webTestClient
            .get()
            .uri("/api/v1/skill-proposals/nonexistent-id")
            .exchange()
            .expectStatus()
            .isNotFound
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("RESOURCE_NOT_FOUND")
    }

    @Test
    fun `PATCH approve transitions the proposal to APPROVED`() {
        val saved =
            repository
                .save(
                    SkillProposalFixture.skillProposal(
                        id = null,
                        status = SkillProposalStatus.PROPOSED,
                    ),
                ).block()!!

        webTestClient
            .patch()
            .uri("/api/v1/skill-proposals/${saved.id}/approve?reviewedBy=alice")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("APPROVED")
            .jsonPath("$.reviewedBy")
            .isEqualTo("alice")
    }

    @Test
    fun `PATCH approve returns 409 when proposal is already approved`() {
        val saved =
            repository
                .save(
                    SkillProposalFixture.skillProposal(
                        id = null,
                        status = SkillProposalStatus.APPROVED,
                    ),
                ).block()!!

        webTestClient
            .patch()
            .uri("/api/v1/skill-proposals/${saved.id}/approve?reviewedBy=alice")
            .exchange()
            .expectStatus()
            .is4xxClientError
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("INVALID_STATE_TRANSITION")
    }

    @Test
    fun `PATCH reject transitions the proposal to REJECTED`() {
        val saved =
            repository
                .save(
                    SkillProposalFixture.skillProposal(
                        id = null,
                        status = SkillProposalStatus.PROPOSED,
                    ),
                ).block()!!

        webTestClient
            .patch()
            .uri("/api/v1/skill-proposals/${saved.id}/reject?reviewedBy=bob")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("REJECTED")
    }

    @Test
    fun `DELETE proposal returns 204 and removes it`() {
        val saved =
            repository
                .save(SkillProposalFixture.skillProposal(id = null))
                .block()!!

        webTestClient
            .delete()
            .uri("/api/v1/skill-proposals/${saved.id}")
            .exchange()
            .expectStatus()
            .isNoContent

        val remaining = repository.findById(saved.id!!).block()
        assertThat(remaining).isNull()
    }

    @Test
    fun `POST proposal returns 400 when required fields are blank`() {
        val request =
            CreateSkillProposalRequest(
                title = "  ",
                problem = "  ",
                proposedToolName = "  ",
                motivation = "  ",
            )

        webTestClient
            .post()
            .uri("/api/v1/skill-proposals")
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
