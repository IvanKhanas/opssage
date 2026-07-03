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
package com.opssage.knowledge.mcp

import com.opssage.knowledge.model.SkillProposal
import com.opssage.knowledge.model.SkillProposalDraft
import com.opssage.knowledge.service.SkillProposalService
import com.opssage.knowledge.util.blockingGet

import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component

@Component
class SkillMcpTools(
    private val skillProposalService: SkillProposalService,
) {

    @Tool(
        description =
            "Propose a new read-only diagnostic tool (skill) that would have " +
                "helped the current investigation but does not yet exist. " +
                "The proposal is stored for human review and never changes " +
                "the running system. Describe the problem the tool solves, " +
                "the suggested tool name, its expected inputs and outputs, " +
                "why it is needed, and example situations where it applies.",
    )
    fun proposeNewSkill(
        title: String,
        problem: String,
        proposedToolName: String,
        expectedInputs: List<String>,
        expectedOutputs: List<String>,
        motivation: String,
        examples: List<String>,
    ): SkillProposal =
        skillProposalService
            .propose(
                SkillProposalDraft(
                    title = title,
                    problem = problem,
                    proposedToolName = proposedToolName,
                    expectedInputs = expectedInputs,
                    expectedOutputs = expectedOutputs,
                    motivation = motivation,
                    examples = examples,
                ),
            ).blockingGet()
            ?: error("Skill proposal was not persisted")
}
