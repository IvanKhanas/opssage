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
package com.opssage.knowledge.dto

import com.opssage.knowledge.model.SkillProposalDraft
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateSkillProposalRequest(
    @field:NotBlank
    @field:Size(max = 256)
    val title: String,
    @field:NotBlank
    @field:Size(max = 4_000)
    val problem: String,
    @field:NotBlank
    @field:Size(max = 128)
    val proposedToolName: String,
    @field:Size(max = 50)
    val expectedInputs: List<
        @NotBlank
        @Size(max = 256)
        String,
    > = emptyList(),
    @field:Size(max = 50)
    val expectedOutputs: List<
        @NotBlank
        @Size(max = 256)
        String,
    > = emptyList(),
    @field:NotBlank
    @field:Size(max = 4_000)
    val motivation: String,
    @field:Size(max = 50)
    val examples: List<
        @NotBlank
        @Size(max = 2_000)
        String,
    > = emptyList(),
) {

    fun toDraft(): SkillProposalDraft =
        SkillProposalDraft(
            title = title,
            problem = problem,
            proposedToolName = proposedToolName,
            expectedInputs = expectedInputs,
            expectedOutputs = expectedOutputs,
            motivation = motivation,
            examples = examples,
        )
}
