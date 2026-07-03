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
package com.opssage.knowledge.unit

import com.opssage.knowledge.exception.ResourceNotFoundException
import com.opssage.knowledge.model.KnownIncident
import com.opssage.knowledge.repository.KnownIncidentRepository
import com.opssage.knowledge.service.KnownIncidentService
import com.opssage.knowledge.unit.fixture.KnownIncidentFixture
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import java.time.Instant

@ExtendWith(MockKExtension::class)
class KnownIncidentServiceTest {

    @MockK
    lateinit var repository: KnownIncidentRepository

    private lateinit var service: KnownIncidentService

    @BeforeEach
    fun setUp() {
        service = KnownIncidentService(repository)
    }

    @Test
    fun `findById returns incident when found`() {
        val incident = KnownIncidentFixture.knownIncident(id = "inc-1")
        every { repository.findById("inc-1") } returns Mono.just(incident)

        val result = service.findById("inc-1").block()!!

        assertThat(result.id).isEqualTo("inc-1")
    }

    @Test
    fun `findById throws when incident is missing`() {
        every { repository.findById("missing") } returns Mono.empty()

        assertThatThrownBy { service.findById("missing").block() }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun `findAll delegates to repository`() {
        val incidents =
            listOf(
                KnownIncidentFixture.knownIncident(),
                KnownIncidentFixture.knownIncident(),
            )
        every { repository.findAll() } returns Flux.fromIterable(incidents)

        val result = service.findAll().collectList().block()!!

        assertThat(result).hasSize(2)
        verify(exactly = 1) { repository.findAll() }
    }

    @Test
    fun `findByService delegates to repository with serviceId`() {
        val incident =
            KnownIncidentFixture.knownIncident(serviceId = "payment-svc")
        every { repository.findByServiceId("payment-svc") } returns
            Flux.just(incident)

        val result =
            service.findByService("payment-svc").collectList().block()!!

        assertThat(result).hasSize(1)
        assertThat(result.first().serviceId).isEqualTo("payment-svc")
    }

    @Test
    fun `searchByTitle delegates to repository with keyword`() {
        val incident = KnownIncidentFixture.knownIncident(title = "OOM crash")
        every { repository.findByTitleContainingIgnoreCase("oom") } returns
            Flux.just(incident)

        val result = service.searchByTitle("oom").collectList().block()!!

        assertThat(result).hasSize(1)
    }

    @Test
    fun `findByRelatedService delegates to repository with serviceId`() {
        val incident =
            KnownIncidentFixture.knownIncident(
                relatedServices = listOf("auth-svc"),
            )
        every { repository.findByRelatedServicesContains("auth-svc") } returns
            Flux.just(incident)

        val result =
            service.findByRelatedService("auth-svc").collectList().block()!!

        assertThat(result).hasSize(1)
        assertThat(result.first().relatedServices).contains("auth-svc")
    }

    @Test
    fun `create saves and returns the incident`() {
        val incident = KnownIncidentFixture.knownIncident(id = null)
        val persisted = incident.copy(id = "new-id")
        every { repository.save(incident) } returns Mono.just(persisted)

        val result = service.create(incident).block()!!

        assertThat(result.id).isEqualTo("new-id")
    }

    @Test
    fun `delete removes incident when it exists`() {
        val incident = KnownIncidentFixture.knownIncident(id = "inc-del")
        every { repository.findById("inc-del") } returns Mono.just(incident)
        every { repository.deleteById("inc-del") } returns Mono.empty()

        service.delete("inc-del").block()

        verify(exactly = 1) { repository.deleteById("inc-del") }
    }

    @Test
    fun `delete throws when incident is missing`() {
        every { repository.findById("missing") } returns Mono.empty()

        assertThatThrownBy { service.delete("missing").block() }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun `update preserves original id from existing record`() {
        val existing = KnownIncidentFixture.knownIncident(id = "original-id")
        val incoming =
            KnownIncidentFixture.knownIncident(
                id = "ignored-id",
                title = "New Title",
            )
        val saved = slot<KnownIncident>()
        every { repository.findById("original-id") } returns Mono.just(existing)
        every { repository.save(capture(saved)) } answers
            { Mono.just(saved.captured) }

        service.update("original-id", incoming).block()

        assertThat(saved.captured.id).isEqualTo("original-id")
    }

    @Test
    fun `update preserves original createdAt from existing record`() {
        val originalCreatedAt = Instant.parse("2026-01-01T00:00:00Z")
        val existing =
            KnownIncidentFixture
                .knownIncident(id = "inc-1")
                .copy(createdAt = originalCreatedAt)
        val incoming = KnownIncidentFixture.knownIncident(title = "Updated")
        val saved = slot<KnownIncident>()
        every { repository.findById("inc-1") } returns Mono.just(existing)
        every { repository.save(capture(saved)) } answers
            { Mono.just(saved.captured) }

        service.update("inc-1", incoming).block()

        assertThat(saved.captured.createdAt).isEqualTo(originalCreatedAt)
    }

    @Test
    fun `update sets updatedAt to a time after the existing updatedAt`() {
        val past = Instant.parse("2026-01-01T00:00:00Z")
        val existing =
            KnownIncidentFixture
                .knownIncident(id = "inc-3")
                .copy(updatedAt = past)
        val incoming = KnownIncidentFixture.knownIncident()
        val saved = slot<KnownIncident>()
        every { repository.findById("inc-3") } returns Mono.just(existing)
        every { repository.save(capture(saved)) } answers
            { Mono.just(saved.captured) }

        service.update("inc-3", incoming).block()

        assertThat(saved.captured.updatedAt).isAfter(past)
    }
}
