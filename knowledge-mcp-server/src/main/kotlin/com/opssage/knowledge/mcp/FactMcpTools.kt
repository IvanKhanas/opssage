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

import com.opssage.knowledge.config.McpProperties
import com.opssage.knowledge.config.PaginationProperties
import com.opssage.knowledge.model.Confidence
import com.opssage.knowledge.model.Fact
import com.opssage.knowledge.model.FactProposal
import com.opssage.knowledge.service.FactService
import com.opssage.knowledge.util.blockingGet
import com.opssage.knowledge.util.blockingList
import com.opssage.knowledge.util.paged

import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component

@Component
class FactMcpTools(
    private val factService: FactService,
    private val pagination: PaginationProperties,
    private val mcp: McpProperties,
) {

    @Tool(
        description =
            "Retrieve all approved diagnostic facts for a given service. " +
                "Use this to check if similar incidents have been investigated before.",
    )
    fun getFactsForService(
        serviceId: String,
        limit: Int?,
    ): List<Fact> =
        factService
            .findApprovedByService(serviceId)
            .paged(0, pagination.resolveSize(limit))
            .blockingList(mcp.callTimeout)

    @Tool(
        description =
            "Semantically search approved facts by a free-text symptom " +
                "description. Returns the facts whose symptom and root cause " +
                "are most similar in meaning to the query, ranked by " +
                "relevance. Optionally restrict the search to a single " +
                "service. Prefer this over exact keyword matching: it finds " +
                "related incidents even when the wording differs.",
    )
    fun searchFacts(
        symptoms: String,
        serviceId: String?,
        limit: Int?,
    ): List<Fact> =
        factService
            .searchApproved(symptoms, serviceId, pagination.resolveSize(limit))
            .blockingList(mcp.callTimeout)

    @Tool(
        description =
            "Propose a new diagnostic fact discovered during an " +
                "investigation. The fact is stored as PROPOSED and is never " +
                "trusted automatically: a human must review and approve it " +
                "before it becomes searchable. Provide the affected service, " +
                "the observed symptom, the root cause, an optional " +
                "resolution, a confidence (LOW, MEDIUM or HIGH) and the " +
                "originating investigation id.",
    )
    fun proposeInvestigationFact(
        serviceId: String,
        symptom: String,
        rootCause: String,
        resolution: String?,
        confidence: Confidence,
        investigationId: String?,
    ): Fact =
        factService
            .create(
                FactProposal(
                    serviceId = serviceId,
                    symptom = symptom,
                    rootCause = rootCause,
                    resolution = resolution,
                    confidence = confidence,
                    investigationId = investigationId,
                ),
            ).blockingGet(mcp.callTimeout)
            ?: error("Fact proposal was not persisted")
}
