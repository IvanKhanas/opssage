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
import com.opssage.knowledge.model.Runbook
import com.opssage.knowledge.repository.RunbookRepository
import com.opssage.knowledge.service.RunbookService
import com.opssage.knowledge.unit.fixture.RunbookFixture
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
class RunbookServiceTest {

    @MockK
    lateinit var repository: RunbookRepository

    private lateinit var service: RunbookService

    @BeforeEach
    fun setUp() {
        service = RunbookService(repository)
    }

    @Test
    fun `findById returns runbook when found`() {
        val runbook = RunbookFixture.runbook(id = "rb-1")
        every { repository.findById("rb-1") } returns Mono.just(runbook)

        val result = service.findById("rb-1").block()!!

        assertThat(result.id).isEqualTo("rb-1")
    }

    @Test
    fun `findById throws when runbook is missing`() {
        every { repository.findById("missing") } returns Mono.empty()

        assertThatThrownBy { service.findById("missing").block() }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun `findAll delegates to repository`() {
        val runbooks =
            listOf(RunbookFixture.runbook(), RunbookFixture.runbook())
        every { repository.findAll() } returns Flux.fromIterable(runbooks)

        val result = service.findAll().collectList().block()!!

        assertThat(result).hasSize(2)
        verify(exactly = 1) { repository.findAll() }
    }

    @Test
    fun `findByService delegates to repository with serviceId`() {
        val runbook = RunbookFixture.runbook(serviceId = "payment-svc")
        every { repository.findByServiceId("payment-svc") } returns
            Flux.just(runbook)

        val result =
            service
                .findByService(
                    "payment-svc",
                ).collectList()
                .block()!!

        assertThat(result).hasSize(1)
        assertThat(result.first().serviceId).isEqualTo("payment-svc")
    }

    @Test
    fun `findByAlert delegates to repository with alertName`() {
        val runbook = RunbookFixture.runbook(alertName = "HighLatency")
        every { repository.findByAlertName("HighLatency") } returns
            Flux.just(runbook)

        val result = service.findByAlert("HighLatency").collectList().block()!!

        assertThat(result).hasSize(1)
        assertThat(result.first().alertName).isEqualTo("HighLatency")
    }

    @Test
    fun `create saves and returns the runbook`() {
        val runbook = RunbookFixture.runbook(id = null)
        val persisted = runbook.copy(id = "new-id")
        every { repository.save(runbook) } returns Mono.just(persisted)

        val result = service.create(runbook).block()!!

        assertThat(result.id).isEqualTo("new-id")
    }

    @Test
    fun `delete removes runbook when it exists`() {
        val runbook = RunbookFixture.runbook(id = "rb-del")
        every { repository.findById("rb-del") } returns Mono.just(runbook)
        every { repository.deleteById("rb-del") } returns Mono.empty()

        service.delete("rb-del").block()

        verify(exactly = 1) { repository.deleteById("rb-del") }
    }

    @Test
    fun `delete throws when runbook is missing`() {
        every { repository.findById("missing") } returns Mono.empty()

        assertThatThrownBy { service.delete("missing").block() }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun `update preserves original id from existing record`() {
        val existing = RunbookFixture.runbook(id = "original-id")
        val incoming =
            RunbookFixture.runbook(
                id = "ignored-id",
                title = "New Title",
            )
        val saved = slot<Runbook>()
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
            RunbookFixture
                .runbook(
                    id = "rb-1",
                ).copy(createdAt = originalCreatedAt)
        val incoming = RunbookFixture.runbook(title = "Updated")
        val saved = slot<Runbook>()
        every { repository.findById("rb-1") } returns Mono.just(existing)
        every { repository.save(capture(saved)) } answers
            { Mono.just(saved.captured) }

        service.update("rb-1", incoming).block()

        assertThat(saved.captured.createdAt).isEqualTo(originalCreatedAt)
    }

    @Test
    fun `update applies new title from incoming runbook`() {
        val existing = RunbookFixture.runbook(id = "rb-2", title = "Old Title")
        val incoming = RunbookFixture.runbook(title = "New Title")
        val saved = slot<Runbook>()
        every { repository.findById("rb-2") } returns Mono.just(existing)
        every { repository.save(capture(saved)) } answers
            { Mono.just(saved.captured) }

        service.update("rb-2", incoming).block()

        assertThat(saved.captured.title).isEqualTo("New Title")
    }

    @Test
    fun `update sets updatedAt to a time after the existing updatedAt`() {
        val past = Instant.parse("2026-01-01T00:00:00Z")
        val existing =
            RunbookFixture
                .runbook(
                    id = "rb-3",
                ).copy(updatedAt = past)
        val incoming = RunbookFixture.runbook()
        val saved = slot<Runbook>()
        every { repository.findById("rb-3") } returns Mono.just(existing)
        every { repository.save(capture(saved)) } answers
            { Mono.just(saved.captured) }

        service.update("rb-3", incoming).block()

        assertThat(saved.captured.updatedAt).isAfter(past)
    }
}
