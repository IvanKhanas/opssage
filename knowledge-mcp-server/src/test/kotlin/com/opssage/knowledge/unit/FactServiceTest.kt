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

import com.opssage.knowledge.exception.InvalidFactStateException
import com.opssage.knowledge.exception.InvalidRequestException
import com.opssage.knowledge.exception.ResourceNotFoundException
import com.opssage.knowledge.model.Fact
import com.opssage.knowledge.model.FactProposal
import com.opssage.knowledge.model.FactStatus
import com.opssage.knowledge.model.FactVerdict
import com.opssage.knowledge.repository.FactRepository
import com.opssage.knowledge.repository.FactVectorIndex
import com.opssage.knowledge.service.FactService
import com.opssage.knowledge.unit.fixture.FactFixture
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import org.springframework.data.domain.Vector

@ExtendWith(MockKExtension::class)
class FactServiceTest {

    @MockK
    lateinit var repository: FactRepository

    @MockK
    lateinit var index: FactVectorIndex

    private lateinit var service: FactService

    @BeforeEach
    fun setUp() {
        service = FactService(repository, index)
    }

    @Test
    fun `approve sets status APPROVED with approvedBy and approvedAt`() {
        val fact = FactFixture.fact(id = "fact-1", status = FactStatus.PROPOSED)
        val saved = slot<Fact>()
        every { repository.findById("fact-1") } returns Mono.just(fact)
        every { index.embedding(fact) } returns Mono.just(EMBEDDING)
        every { repository.save(capture(saved)) } answers
            { Mono.just(saved.captured) }

        val result = service.approve("fact-1", "sre-bot").block()!!

        assertThat(result.status).isEqualTo(FactStatus.APPROVED)
        assertThat(result.approvedBy).isEqualTo("sre-bot")
        assertThat(result.approvedAt).isNotNull()
        assertThat(result.embedding).isEqualTo(EMBEDDING)
    }

    @Test
    fun `approve preserves all non-status fields unchanged`() {
        val fact = FactFixture.fact(id = "fact-2", symptom = "high latency")
        val saved = slot<Fact>()
        every { repository.findById("fact-2") } returns Mono.just(fact)
        every { index.embedding(fact) } returns Mono.just(EMBEDDING)
        every { repository.save(capture(saved)) } answers
            { Mono.just(saved.captured) }

        service.approve("fact-2", "ops").block()

        assertThat(saved.captured.symptom).isEqualTo("high latency")
        assertThat(saved.captured.serviceId).isEqualTo(fact.serviceId)
        assertThat(saved.captured.rootCause).isEqualTo(fact.rootCause)
    }

    @ParameterizedTest
    @EnumSource(
        value = FactStatus::class,
        names = ["APPROVED", "REJECTED"],
    )
    fun `approve rejects finalized facts`(status: FactStatus) {
        every {
            repository.findById("fact-2")
        } returns Mono.just(FactFixture.fact(status = status))

        assertThatThrownBy {
            service.approve("fact-2", "sre-bot").block()
        }.isInstanceOf(InvalidFactStateException::class.java)

        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `approve rejects a blank approver`() {
        assertThatThrownBy {
            service.approve("fact-2", " ").block()
        }.isInstanceOf(InvalidRequestException::class.java)

        verify(exactly = 0) { repository.findById(any()) }
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `create stores a proposal as a new unapproved fact`() {
        val proposal =
            FactProposal(
                serviceId = "payment-svc",
                symptom = "high latency",
                rootCause = "connection pool exhausted",
            )
        val saved = slot<Fact>()
        every { index.embedding(any()) } returns Mono.just(EMBEDDING)
        every { repository.save(capture(saved)) } answers {
            Mono.just(saved.captured)
        }

        service.create(proposal).block()

        assertThat(saved.captured.id).isNull()
        assertThat(saved.captured.status).isEqualTo(FactStatus.PROPOSED)
        assertThat(saved.captured.approvedBy).isNull()
        assertThat(saved.captured.approvedAt).isNull()
        assertThat(saved.captured.embedding).isEqualTo(EMBEDDING)
    }

    @ParameterizedTest
    @EnumSource(FactVerdict::class)
    fun `create carries the proposal verdict onto the stored fact`(
        verdict: FactVerdict,
    ) {
        val proposal =
            FactProposal(
                serviceId = "payment-svc",
                symptom = "high latency",
                rootCause = "connection pool exhausted",
                verdict = verdict,
            )
        val saved = slot<Fact>()
        every { index.embedding(any()) } returns Mono.just(EMBEDDING)
        every { repository.save(capture(saved)) } answers {
            Mono.just(saved.captured)
        }

        service.create(proposal).block()

        assertThat(saved.captured.verdict).isEqualTo(verdict)
    }

    @Test
    fun `findById fails when fact does not exist`() {
        every { repository.findById("missing") } returns Mono.empty()

        assertThatThrownBy {
            service.findById("missing").block()
        }.isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun `reject sets status to REJECTED`() {
        val fact = FactFixture.fact(id = "fact-3", status = FactStatus.PROPOSED)
        val saved = slot<Fact>()
        every { repository.findById("fact-3") } returns Mono.just(fact)
        every { repository.save(capture(saved)) } answers
            { Mono.just(saved.captured) }

        val result = service.reject("fact-3").block()!!

        assertThat(result.status).isEqualTo(FactStatus.REJECTED)
    }

    @Test
    fun `reject does not set approvedBy or approvedAt`() {
        val fact = FactFixture.fact(id = "fact-4")
        val saved = slot<Fact>()
        every { repository.findById("fact-4") } returns Mono.just(fact)
        every { repository.save(capture(saved)) } answers
            { Mono.just(saved.captured) }

        service.reject("fact-4").block()

        assertThat(saved.captured.approvedBy).isNull()
        assertThat(saved.captured.approvedAt).isNull()
    }

    @ParameterizedTest
    @EnumSource(
        value = FactStatus::class,
        names = ["APPROVED", "REJECTED"],
    )
    fun `reject rejects finalized facts`(status: FactStatus) {
        every {
            repository.findById("fact-4")
        } returns Mono.just(FactFixture.fact(status = status))

        assertThatThrownBy {
            service.reject("fact-4").block()
        }.isInstanceOf(InvalidFactStateException::class.java)

        verify(exactly = 0) { repository.save(any()) }
    }

    @ParameterizedTest
    @EnumSource(FactStatus::class)
    fun `findByStatus delegates to repository for every status variant`(
        status: FactStatus,
    ) {
        every { repository.findByStatus(status) } returns Flux.empty()

        service.findByStatus(status).collectList().block()

        verify(exactly = 1) { repository.findByStatus(status) }
    }

    @Test
    fun `findApprovedByService always passes APPROVED to repository`() {
        every {
            repository.findByServiceIdAndStatus(
                "payment-svc",
                FactStatus.APPROVED,
            )
        } returns Flux.empty()

        service.findApprovedByService("payment-svc").collectList().block()

        verify {
            repository.findByServiceIdAndStatus(
                "payment-svc",
                FactStatus.APPROVED,
            )
        }
    }

    @Test
    fun `semantic search delegates query and filters to vector index`() {
        val expected = FactFixture.fact(status = FactStatus.APPROVED)
        every {
            index.search("connection timeout", "payment-svc", 5)
        } returns Flux.just(expected)

        val result =
            service
                .searchApproved("connection timeout", "payment-svc", 5)
                .collectList()
                .block()

        assertThat(result).containsExactly(expected)
        verify {
            index.search("connection timeout", "payment-svc", 5)
        }
    }

    private companion object {
        val EMBEDDING: Vector = Vector.of(0.1, 0.2, 0.3)
    }
}
