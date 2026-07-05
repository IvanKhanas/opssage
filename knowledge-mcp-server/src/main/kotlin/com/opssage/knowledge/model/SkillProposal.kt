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
package com.opssage.knowledge.model

import java.time.Instant

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "skill_proposals")
data class SkillProposal(
    @Id val id: String? = null,
    @Version val version: Long? = null,
    val title: String,
    val problem: String,
    @Indexed val proposedToolName: String,
    val expectedInputs: List<String> = emptyList(),
    val expectedOutputs: List<String> = emptyList(),
    val motivation: String,
    val examples: List<String> = emptyList(),
    val status: SkillProposalStatus = SkillProposalStatus.PROPOSED,
    val createdAt: Instant = Instant.now(),
    val reviewedBy: String? = null,
    val reviewedAt: Instant? = null,
)
