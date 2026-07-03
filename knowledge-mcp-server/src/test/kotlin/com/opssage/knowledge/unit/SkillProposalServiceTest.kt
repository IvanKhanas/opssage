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

import com.opssage.knowledge.exception.InvalidRequestException
import com.opssage.knowledge.exception.InvalidSkillProposalStateException
import com.opssage.knowledge.exception.ResourceNotFoundException
import com.opssage.knowledge.model.SkillProposal
import com.opssage.knowledge.model.SkillProposalDraft
import com.opssage.knowledge.model.SkillProposalStatus
import com.opssage.knowledge.repository.SkillProposalRepository
import com.opssage.knowledge.service.SkillProposalService
import com.opssage.knowledge.unit.fixture.SkillProposalFixture
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
class SkillProposalServiceTest {

    @MockK
    lateinit var repository: SkillProposalRepository

    private lateinit var service: SkillProposalService

    @BeforeEach
    fun setUp() {
        service = SkillProposalService(repository)
    }

    private fun draft(): SkillProposalDraft =
        SkillProposalDraft(
            title = "Detect slow Mongo queries",
            problem = "No tool surfaces slow Mongo queries per service",
            proposedToolName = "get_slow_mongo_queries",
            expectedInputs = listOf("serviceId"),
            expectedOutputs = listOf("query", "p99"),
            motivation = "Would speed up latency investigations",
            examples = listOf("payment-svc latency spike"),
        )

    @Test
    fun `findById returns proposal when found`() {
        val proposal = SkillProposalFixture.skillProposal(id = "skill-1")
        every { repository.findById("skill-1") } returns Mono.just(proposal)

        val result = service.findById("skill-1").block()!!

        assertThat(result.id).isEqualTo("skill-1")
    }

    @Test
    fun `findById throws when proposal is missing`() {
        every { repository.findById("missing") } returns Mono.empty()

        assertThatThrownBy { service.findById("missing").block() }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun `findByStatus delegates to repository`() {
        val proposals =
            listOf(
                SkillProposalFixture.skillProposal(),
                SkillProposalFixture.skillProposal(),
            )
        every { repository.findByStatus(SkillProposalStatus.PROPOSED) } returns
            Flux.fromIterable(proposals)

        val result =
            service
                .findByStatus(SkillProposalStatus.PROPOSED)
                .collectList()
                .block()!!

        assertThat(result).hasSize(2)
    }

    @Test
    fun `propose saves a PROPOSED proposal from the draft`() {
        val saved = slot<SkillProposal>()
        every { repository.save(capture(saved)) } answers
            { Mono.just(saved.captured) }

        val result = service.propose(draft()).block()!!

        assertThat(result.status).isEqualTo(SkillProposalStatus.PROPOSED)
        assertThat(result.proposedToolName).isEqualTo("get_slow_mongo_queries")
    }

    @Test
    fun `approve transitions proposal to APPROVED and records reviewer`() {
        val existing =
            SkillProposalFixture.skillProposal(
                id = "skill-1",
                status = SkillProposalStatus.PROPOSED,
            )
        val saved = slot<SkillProposal>()
        every { repository.findById("skill-1") } returns Mono.just(existing)
        every { repository.save(capture(saved)) } answers
            { Mono.just(saved.captured) }

        service.approve("skill-1", "alice").block()

        assertThat(saved.captured.status)
            .isEqualTo(SkillProposalStatus.APPROVED)
        assertThat(saved.captured.reviewedBy).isEqualTo("alice")
        assertThat(saved.captured.reviewedAt).isNotNull()
    }

    @Test
    fun `approve rejects a blank reviewer`() {
        assertThatThrownBy { service.approve("skill-1", " ").block() }
            .isInstanceOf(InvalidRequestException::class.java)
    }

    @Test
    fun `approve fails when proposal is not in PROPOSED state`() {
        val existing =
            SkillProposalFixture.skillProposal(
                id = "skill-1",
                status = SkillProposalStatus.APPROVED,
            )
        every { repository.findById("skill-1") } returns Mono.just(existing)

        assertThatThrownBy { service.approve("skill-1", "alice").block() }
            .isInstanceOf(InvalidSkillProposalStateException::class.java)
    }

    @Test
    fun `reject transitions proposal to REJECTED`() {
        val existing =
            SkillProposalFixture.skillProposal(
                id = "skill-1",
                status = SkillProposalStatus.PROPOSED,
            )
        val saved = slot<SkillProposal>()
        every { repository.findById("skill-1") } returns Mono.just(existing)
        every { repository.save(capture(saved)) } answers
            { Mono.just(saved.captured) }

        service.reject("skill-1", "bob").block()

        assertThat(saved.captured.status)
            .isEqualTo(SkillProposalStatus.REJECTED)
        assertThat(saved.captured.reviewedBy).isEqualTo("bob")
    }

    @Test
    fun `reject fails when proposal is not in PROPOSED state`() {
        val existing =
            SkillProposalFixture.skillProposal(
                id = "skill-1",
                status = SkillProposalStatus.REJECTED,
            )
        every { repository.findById("skill-1") } returns Mono.just(existing)

        assertThatThrownBy { service.reject("skill-1", "bob").block() }
            .isInstanceOf(InvalidSkillProposalStateException::class.java)
    }

    @Test
    fun `delete removes proposal when it exists`() {
        val existing = SkillProposalFixture.skillProposal(id = "skill-del")
        every { repository.findById("skill-del") } returns Mono.just(existing)
        every { repository.deleteById("skill-del") } returns Mono.empty()

        service.delete("skill-del").block()

        verify(exactly = 1) { repository.deleteById("skill-del") }
    }

    @Test
    fun `delete throws when proposal is missing`() {
        every { repository.findById("missing") } returns Mono.empty()

        assertThatThrownBy { service.delete("missing").block() }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }
}
