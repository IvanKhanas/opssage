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
import com.opssage.knowledge.model.Confidence
import com.opssage.knowledge.model.InvestigationSummary
import com.opssage.knowledge.model.InvestigationSummaryDraft
import com.opssage.knowledge.repository.InvestigationSummaryRepository
import com.opssage.knowledge.service.InvestigationSummaryService
import com.opssage.knowledge.unit.fixture.InvestigationSummaryFixture
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

@ExtendWith(MockKExtension::class)
class InvestigationSummaryServiceTest {

    @MockK
    lateinit var repository: InvestigationSummaryRepository

    private lateinit var service: InvestigationSummaryService

    @BeforeEach
    fun setUp() {
        service = InvestigationSummaryService(repository)
    }

    private fun draft(): InvestigationSummaryDraft =
        InvestigationSummaryDraft(
            investigationId = "inv-1",
            serviceId = "payment-svc",
            summary = "Latency spike traced to slow Mongo query",
            mostLikelyCause = "Missing index on orders collection",
            confidence = Confidence.HIGH,
            evidence = listOf("p99 grew from 40ms to 900ms"),
            recommendedActions = listOf("Add index on orders.userId"),
        )

    @Test
    fun `findById returns summary when found`() {
        val summary =
            InvestigationSummaryFixture.investigationSummary(id = "sum-1")
        every { repository.findById("sum-1") } returns Mono.just(summary)

        val result = service.findById("sum-1").block()!!

        assertThat(result.id).isEqualTo("sum-1")
    }

    @Test
    fun `findById throws when summary is missing`() {
        every { repository.findById("missing") } returns Mono.empty()

        assertThatThrownBy { service.findById("missing").block() }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun `findByInvestigation delegates to repository`() {
        val summaries =
            listOf(
                InvestigationSummaryFixture.investigationSummary(
                    investigationId = "inv-1",
                ),
            )
        every { repository.findByInvestigationId("inv-1") } returns
            Flux.fromIterable(summaries)

        val result =
            service.findByInvestigation("inv-1").collectList().block()!!

        assertThat(result).hasSize(1)
    }

    @Test
    fun `findByService delegates to repository`() {
        val summaries =
            listOf(
                InvestigationSummaryFixture.investigationSummary(
                    serviceId = "payment-svc",
                ),
                InvestigationSummaryFixture.investigationSummary(
                    serviceId = "payment-svc",
                ),
            )
        every { repository.findByServiceId("payment-svc") } returns
            Flux.fromIterable(summaries)

        val result =
            service.findByService("payment-svc").collectList().block()!!

        assertThat(result).hasSize(2)
    }

    @Test
    fun `save persists a summary built from the draft`() {
        val saved = slot<InvestigationSummary>()
        every { repository.save(capture(saved)) } answers
            { Mono.just(saved.captured) }

        val result = service.save(draft()).block()!!

        assertThat(result.investigationId).isEqualTo("inv-1")
        assertThat(result.serviceId).isEqualTo("payment-svc")
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
        assertThat(result.mostLikelyCause)
            .isEqualTo("Missing index on orders collection")
    }

    @Test
    fun `delete removes summary when it exists`() {
        val existing =
            InvestigationSummaryFixture.investigationSummary(id = "sum-del")
        every { repository.findById("sum-del") } returns Mono.just(existing)
        every { repository.deleteById("sum-del") } returns Mono.empty()

        service.delete("sum-del").block()

        verify(exactly = 1) { repository.deleteById("sum-del") }
    }

    @Test
    fun `delete throws when summary is missing`() {
        every { repository.findById("missing") } returns Mono.empty()

        assertThatThrownBy { service.delete("missing").block() }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }
}
