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
package com.opssage.knowledge.unit.fixture

import com.opssage.knowledge.model.SkillProposal
import com.opssage.knowledge.model.SkillProposalStatus
import net.datafaker.Faker

import java.util.UUID

object SkillProposalFixture {

    private val faker = Faker()

    fun skillProposal(
        id: String? = UUID.randomUUID().toString(),
        title: String = faker.lorem().sentence(3),
        problem: String = faker.lorem().paragraph(),
        proposedToolName: String = "get_${faker.lorem().word()}_status",
        expectedInputs: List<String> = listOf("serviceId"),
        expectedOutputs: List<String> = listOf("status", "confidence"),
        motivation: String = faker.lorem().sentence(),
        examples: List<String> = listOf(faker.lorem().sentence()),
        status: SkillProposalStatus = SkillProposalStatus.PROPOSED,
    ) = SkillProposal(
        id = id,
        title = title,
        problem = problem,
        proposedToolName = proposedToolName,
        expectedInputs = expectedInputs,
        expectedOutputs = expectedOutputs,
        motivation = motivation,
        examples = examples,
        status = status,
    )
}
