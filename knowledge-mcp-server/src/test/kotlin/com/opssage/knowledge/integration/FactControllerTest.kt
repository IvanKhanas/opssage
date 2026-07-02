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

import com.opssage.knowledge.dto.CreateFactRequest
import com.opssage.knowledge.model.Fact
import com.opssage.knowledge.model.FactStatus
import com.opssage.knowledge.repository.FactMongoRepository
import com.opssage.knowledge.testcontainers.TestContainersConfiguration
import com.opssage.knowledge.unit.fixture.FactFixture
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
class FactControllerTest {

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
    lateinit var repository: FactMongoRepository

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
    fun `POST fact returns 201 and persists the fact`() {
        val fact = FactFixture.fact(id = null)
        val request =
            CreateFactRequest(
                serviceId = fact.serviceId,
                symptom = fact.symptom,
                rootCause = fact.rootCause,
                resolution = fact.resolution,
                tags = fact.tags,
                confidence = fact.confidence,
                investigationId = fact.investigationId,
            )

        val created =
            webTestClient
                .post()
                .uri("/api/v1/facts")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isCreated
                .expectBody(Fact::class.java)
                .returnResult()
                .responseBody!!

        assertThat(created.id).isNotNull()
        assertThat(created.symptom).isEqualTo(fact.symptom)
        assertThat(created.status).isEqualTo(FactStatus.PROPOSED)
    }

    @Test
    fun `GET fact by id returns the stored fact`() {
        val saved = repository.save(FactFixture.fact(id = null)).block()!!

        webTestClient
            .get()
            .uri("/api/v1/facts/${saved.id}")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.id")
            .isEqualTo(saved.id!!)
            .jsonPath("$.symptom")
            .isEqualTo(saved.symptom)
    }

    @Test
    fun `GET facts returns only facts with requested status`() {
        repository
            .saveAll(
                listOf(
                    FactFixture.fact(id = null, status = FactStatus.PROPOSED),
                    FactFixture.fact(id = null, status = FactStatus.PROPOSED),
                    FactFixture.fact(id = null, status = FactStatus.APPROVED),
                ),
            ).collectList()
            .block()

        webTestClient
            .get()
            .uri("/api/v1/facts?status=PROPOSED")
            .exchange()
            .expectStatus()
            .isOk
            .expectBodyList(Fact::class.java)
            .hasSize(2)
    }

    @Test
    fun `PATCH approve changes status to APPROVED`() {
        val saved = repository.save(FactFixture.fact(id = null)).block()!!

        webTestClient
            .patch()
            .uri("/api/v1/facts/${saved.id}/approve?approvedBy=sre-bot")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("APPROVED")
            .jsonPath("$.approvedBy")
            .isEqualTo("sre-bot")
    }

    @Test
    fun `PATCH reject changes status to REJECTED`() {
        val saved = repository.save(FactFixture.fact(id = null)).block()!!

        webTestClient
            .patch()
            .uri("/api/v1/facts/${saved.id}/reject")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("REJECTED")
    }

    @Test
    fun `DELETE fact returns 204 and removes the fact`() {
        val saved = repository.save(FactFixture.fact(id = null)).block()!!

        webTestClient
            .delete()
            .uri("/api/v1/facts/${saved.id}")
            .exchange()
            .expectStatus()
            .isNoContent

        val remaining = repository.findById(saved.id!!).block()
        assertThat(remaining).isNull()
    }
}
