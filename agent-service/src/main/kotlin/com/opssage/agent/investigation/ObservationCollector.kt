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
package com.opssage.agent.investigation

import com.opssage.agent.llm.ConversationTurn
import com.opssage.agent.llm.TargetPlanner
import com.opssage.agent.model.AnchorWindow
import com.opssage.agent.model.InvestigationTarget
import com.opssage.agent.model.InvestigationType
import com.opssage.agent.model.Observation
import com.opssage.agent.playbook.AnalyticsPlaybookRunner
import com.opssage.agent.playbook.AnalyticsPrompt
import com.opssage.agent.playbook.AnalyticsRunRequest
import com.opssage.agent.playbook.AnalyticsScope
import com.opssage.agent.playbook.PlaybookRunner

import org.springframework.stereotype.Component

@Component
class ObservationCollector(
    private val targetPlanner: TargetPlanner,
    private val playbooks: PlaybookRunner,
    private val analytics: AnalyticsPlaybookRunner,
) {

    fun collect(request: ObservationRequest): List<Observation> {
        val target =
            targetPlanner.plan(
                request.prompt.history,
                request.prompt.input,
            )
        val literal = playbooks.literal(request.prompt.input)
        analytics(request, target, literal)
            ?.let { return it }
        return target
            ?.let {
                playbooks.run(
                    request.type,
                    it,
                    request.window,
                    request.prompt.input,
                )
            }.orEmpty()
    }

    private fun analytics(
        request: ObservationRequest,
        target: InvestigationTarget?,
        literal: String?,
    ): List<Observation>? =
        analytics.run(
            AnalyticsRunRequest(
                scope = AnalyticsScope(request.type, target),
                window = request.window,
                prompt = AnalyticsPrompt(request.prompt.input, literal),
            ),
        )
}

data class ObservationRequest(
    val type: InvestigationType,
    val prompt: ObservationPrompt,
    val window: AnchorWindow,
)

data class ObservationPrompt(
    val history: List<ConversationTurn>,
    val input: String,
)
