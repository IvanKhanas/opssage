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
package com.opssage.agent.playbook

import com.opssage.agent.masking.IncidentLiteralExtractor
import com.opssage.agent.masking.MaskedToolRegistry
import com.opssage.agent.model.AnchorWindow
import com.opssage.agent.model.InvestigationTarget
import com.opssage.agent.model.InvestigationType
import com.opssage.agent.model.Observation
import io.github.oshai.kotlinlogging.KotlinLogging

import java.time.Duration

import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class PlaybookRunner(
    private val registry: MaskedToolRegistry,
    private val executor: ToolStepExecutor,
    private val literalExtractor: IncidentLiteralExtractor,
) {

    init {
        if (registry.isEmpty()) {
            log.atWarn {
                message = "No MCP tools registered, playbooks are disabled"
            }
        } else {
            val required = Playbooks.toolNames(PROBE)
            val missing = required.filterNot(registry::contains)
            check(missing.isEmpty()) {
                "MCP server does not expose playbook tools: $missing"
            }
        }
    }

    fun run(
        type: InvestigationType,
        target: InvestigationTarget,
        window: AnchorWindow,
        input: String,
    ): List<Observation> {
        if (registry.isEmpty()) {
            return emptyList()
        }
        val context =
            PlaybookContext(
                target = target,
                lookback = Duration.between(window.from, window.to),
                literal = literalExtractor.firstLiteral(input),
            )
        val steps = Playbooks.forType(type).steps(context)
        log.atInfo {
            message = "Executing investigation playbook"
            payload =
                mapOf(
                    "type" to type,
                    "service" to target.service,
                    "namespace" to target.namespace,
                    "steps" to steps.map { it.tool },
                )
        }
        return executor.execute(steps)
    }

    fun literal(input: String): String? = literalExtractor.firstLiteral(input)

    private companion object {
        const val PROBE_VALUE = "probe"

        val PROBE =
            PlaybookContext(
                target = InvestigationTarget(PROBE_VALUE, PROBE_VALUE),
                lookback = Duration.ofHours(1),
                literal = PROBE_VALUE,
            )
    }
}
