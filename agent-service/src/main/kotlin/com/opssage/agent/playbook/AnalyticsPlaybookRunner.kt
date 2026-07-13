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

import com.opssage.agent.catalog.ServiceAliases
import com.opssage.agent.catalog.ServiceCatalog
import com.opssage.agent.config.SreProperties
import com.opssage.agent.model.InvestigationTarget
import com.opssage.agent.model.InvestigationType
import com.opssage.agent.model.Observation
import io.github.oshai.kotlinlogging.KotlinLogging

import java.time.Duration

import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class AnalyticsPlaybookRunner(
    private val catalog: ServiceCatalog,
    private val executor: ToolStepExecutor,
    private val ranking: FleetRanking,
    private val complaint: ComplaintAnalytics,
    private val properties: SreProperties,
) {

    fun run(request: AnalyticsRunRequest): List<Observation>? {
        val services = catalog.services().take(AnalyticsPlans.MAX_SERVICES)
        if (services.isEmpty()) {
            return null
        }
        val lookback =
            Duration.between(request.window.from, request.window.to)
        if (
            request.scope.type ==
            InvestigationType.USER_PROBLEM_INVESTIGATION
        ) {
            return complaint.run(services, lookback, request.prompt.literal)
        }
        if (request.scope.type != InvestigationType.ANALYTICAL_REQUEST) {
            return null
        }
        val selected =
            selectedServices(
                request.prompt.input,
                services,
                request.scope.target,
            )
        if (selected.size >= MIN_COMPARISON_SERVICES) {
            return runComparison(selected, lookback)
        }
        if (request.scope.target == null) {
            return runFleetScan(services, lookback)
        }
        return null
    }

    private fun runComparison(
        services: List<String>,
        lookback: Duration,
    ): List<Observation> {
        val steps =
            services.flatMap { service -> compareSteps(service, lookback) }
        return AnalyticsPlans.plan("multi-service comparison", services) +
            ranked(steps)
    }

    private fun runFleetScan(
        services: List<String>,
        lookback: Duration,
    ): List<Observation> {
        val steps =
            services.flatMap { service -> scanSteps(service, lookback) }
        log.atInfo {
            message = "Executing bounded fleet analytics scan"
            payload = mapOf("services" to services.size)
        }
        return AnalyticsPlans.plan("bounded fleet analytics scan", services) +
            ranked(steps)
    }

    private fun compareSteps(
        service: String,
        lookback: Duration,
    ): List<ToolStep> =
        listOf(
            SreTools.GET_SERVICE_HEALTH,
            SreTools.FIND_TOP_LOG_ERRORS,
            SreTools.GET_SERVICE_CORRECTNESS,
            SreTools.FIND_SERVICE_TRACES,
        ).map { tool -> windowed(tool, service, lookback) }

    private fun scanSteps(
        service: String,
        lookback: Duration,
    ): List<ToolStep> =
        listOf(
            SreTools.GET_SERVICE_HEALTH,
            SreTools.FIND_TOP_LOG_ERRORS,
            SreTools.GET_SERVICE_CORRECTNESS,
        ).map { tool -> windowed(tool, service, lookback) }

    private fun windowed(
        tool: String,
        service: String,
        lookback: Duration,
    ): ToolStep =
        ToolSteps.windowed(tool, service, properties.namespace, lookback)

    private fun ranked(steps: List<ToolStep>): List<Observation> {
        val observations = executor.execute(steps)
        return ranking.observe(observations) + observations
    }

    private fun selectedServices(
        input: String,
        services: List<String>,
        target: InvestigationTarget?,
    ): List<String> {
        val mentioned = ServiceAliases.mentionedIn(input, services)
        val withTarget =
            listOfNotNull(target?.service)
                .filter { it in services } + mentioned
        return withTarget.distinct().take(AnalyticsPlans.MAX_SERVICES)
    }

    private companion object {
        const val MIN_COMPARISON_SERVICES = 2
    }
}
