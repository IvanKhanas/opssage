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

import com.opssage.agent.config.SreProperties
import com.opssage.agent.model.Observation
import com.opssage.agent.tools.ToolOutputReader

import java.time.Duration

import org.springframework.stereotype.Component

@Component
class ComplaintAnalytics(
    private val executor: ToolStepExecutor,
    private val reader: ToolOutputReader,
    private val properties: SreProperties,
) {

    fun run(
        services: List<String>,
        lookback: Duration,
        literal: String?,
    ): List<Observation>? {
        if (literal == null) {
            return null
        }
        val probes =
            services.flatMap { service ->
                listOf(
                    ToolSteps.textSearch(
                        service,
                        properties.namespace,
                        lookback,
                        literal,
                    ),
                    ToolSteps.userTraces(
                        service,
                        properties.namespace,
                        lookback,
                        literal,
                    ),
                )
            }
        val probed = executor.execute(probes)
        val matched = matchedServices(services, probed)
        if (matched.isEmpty()) {
            return AnalyticsPlans.plan(NO_MATCH_STRATEGY, services) + probed
        }
        val followUp =
            matched.flatMap { service -> drillDown(service, lookback) }
        return AnalyticsPlans.plan(MATCH_STRATEGY, matched) +
            probed +
            executor.execute(followUp)
    }

    private fun drillDown(
        service: String,
        lookback: Duration,
    ): List<ToolStep> =
        listOf(
            SreTools.GET_SERVICE_HEALTH,
            SreTools.FIND_TOP_LOG_ERRORS,
            SreTools.GET_SERVICE_CORRECTNESS,
            SreTools.FIND_SERVICE_TRACES,
        ).map { tool ->
            ToolSteps.windowed(tool, service, properties.namespace, lookback)
        }

    private fun matchedServices(
        services: List<String>,
        probed: List<Observation>,
    ): List<String> =
        services.filterIndexed { index, _ ->
            val offset = index * PROBES_PER_SERVICE
            hasHit(probed.getOrNull(offset), ToolFields.TOP_ERRORS) ||
                hasHit(probed.getOrNull(offset + 1), ToolFields.TRACES)
        }

    private fun hasHit(
        observation: Observation?,
        field: String,
    ): Boolean =
        observation != null &&
            observation.succeeded &&
            reader.hasEntries(observation.output, field)

    private companion object {
        const val PROBES_PER_SERVICE = 2

        const val MATCH_STRATEGY =
            "user complaint literal scan, drilling into matched services"

        const val NO_MATCH_STRATEGY =
            "user complaint literal scan, nothing matched the user"
    }
}
