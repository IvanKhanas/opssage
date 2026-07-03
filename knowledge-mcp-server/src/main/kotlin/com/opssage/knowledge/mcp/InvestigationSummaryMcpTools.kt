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

import com.opssage.knowledge.model.Confidence
import com.opssage.knowledge.model.InvestigationSummary
import com.opssage.knowledge.model.InvestigationSummaryDraft
import com.opssage.knowledge.service.InvestigationSummaryService
import com.opssage.knowledge.util.blockingGet

import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component

@Component
class InvestigationSummaryMcpTools(
    private val investigationSummaryService: InvestigationSummaryService,
) {

    @Tool(
        description =
            "Persist the final summary of a completed investigation so it " +
                "can be reused later. Provide the investigation id, the " +
                "affected service, a short human-readable summary, the most " +
                "likely cause, an overall confidence (LOW, MEDIUM or HIGH), " +
                "the supporting evidence, and the recommended follow-up " +
                "actions. This only stores knowledge and never changes the " +
                "running system.",
    )
    fun saveInvestigationSummary(
        investigationId: String,
        serviceId: String,
        summary: String,
        mostLikelyCause: String,
        confidence: Confidence,
        evidence: List<String>,
        recommendedActions: List<String>,
    ): InvestigationSummary =
        investigationSummaryService
            .save(
                InvestigationSummaryDraft(
                    investigationId = investigationId,
                    serviceId = serviceId,
                    summary = summary,
                    mostLikelyCause = mostLikelyCause,
                    confidence = confidence,
                    evidence = evidence,
                    recommendedActions = recommendedActions,
                ),
            ).blockingGet()!!
}
