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
package com.opssage.knowledge.testcontainers

import com.opssage.knowledge.model.SkillProposalStatus
import com.opssage.knowledge.repository.SkillProposalMongoRepository
import com.opssage.knowledge.unit.fixture.SkillProposalFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SkillProposalRepositoryTest {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun mongoProps(registry: DynamicPropertyRegistry) =
            TestContainersConfiguration.configure(registry)
    }

    @Autowired
    lateinit var repository: SkillProposalMongoRepository

    @BeforeEach
    fun cleanUp() {
        repository.deleteAll().block()
    }

    @Test
    fun `findByStatus returns only proposals in the given status`() {
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

        val result =
            repository
                .findByStatus(SkillProposalStatus.PROPOSED)
                .collectList()
                .block()!!

        assertThat(result).hasSize(1)
        assertThat(result.first().status)
            .isEqualTo(SkillProposalStatus.PROPOSED)
    }

    @ParameterizedTest
    @EnumSource(SkillProposalStatus::class)
    fun `findByStatus isolates each status`(status: SkillProposalStatus) {
        SkillProposalStatus.entries.forEach { current ->
            repository
                .save(
                    SkillProposalFixture.skillProposal(
                        id = null,
                        status = current,
                    ),
                ).block()
        }

        val result =
            repository.findByStatus(status).collectList().block()!!

        assertThat(result).hasSize(1)
        assertThat(result.first().status).isEqualTo(status)
    }
}
