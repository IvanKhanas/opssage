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

import com.opssage.agent.model.Observation
import com.opssage.agent.tools.ToolOutputReader
import tools.jackson.databind.JsonNode

import java.util.Locale

import org.springframework.stereotype.Component

@Component
class FleetRanking(
    private val reader: ToolOutputReader,
) {

    fun observe(observations: List<Observation>): List<Observation> {
        val scores = rank(observations)
        if (scores.isEmpty()) {
            return emptyList()
        }
        return listOf(Observation(TOOL, render(scores), succeeded = true))
    }

    fun rank(observations: List<Observation>): List<ServiceScore> {
        val scores = LinkedHashMap<String, ServiceScore>()
        observations
            .filter(Observation::succeeded)
            .forEach { observation ->
                reader
                    .read(observation.output)
                    ?.let { merge(scores, observation.tool, it) }
            }
        return scores.values.sortedWith(ORDER)
    }

    private fun merge(
        scores: MutableMap<String, ServiceScore>,
        tool: String,
        node: JsonNode,
    ) {
        val service = reader.text(node, SERVICE_FIELD) ?: return
        val current = scores[service] ?: ServiceScore(service)
        val updated =
            when (tool) {
                SreTools.GET_SERVICE_HEALTH ->
                    current.copy(
                        errorRate = signal(node, ERROR_RATE),
                        latencyP99 = signal(node, LATENCY_P99),
                    )

                SreTools.FIND_TOP_LOG_ERRORS ->
                    current.copy(errorLogs = errorLogs(node))

                SreTools.GET_SERVICE_CORRECTNESS ->
                    current.copy(correctnessFailure = worstInvariant(node))

                else -> return
            }
        scores[service] = updated
    }

    private fun signal(
        node: JsonNode,
        metric: String,
    ): Double? =
        reader
            .array(node, SIGNALS_FIELD)
            .firstOrNull { reader.text(it, METRIC_NAME_FIELD) == metric }
            ?.get(STATS_FIELD)
            ?.takeIf(JsonNode::isObject)
            ?.let { reader.number(it, LATEST_FIELD) }

    private fun errorLogs(node: JsonNode): Long =
        reader
            .array(node, ToolFields.TOP_ERRORS)
            .sumOf { reader.number(it, COUNT_FIELD)?.toLong() ?: 0L }

    private fun worstInvariant(node: JsonNode): Double? =
        reader
            .array(node, INVARIANTS_FIELD)
            .mapNotNull { reader.number(it, FAILURE_RATIO_FIELD) }
            .maxOrNull()

    private fun render(scores: List<ServiceScore>): String =
        scores
            .mapIndexed { index, score -> "${index + 1}. ${line(score)}" }
            .joinToString(separator = "\n", prefix = HEADER)

    private fun line(score: ServiceScore): String =
        "${score.service} error_rate=${ratio(score.errorRate)} " +
            "p99=${seconds(score.latencyP99)} " +
            "error_logs=${score.errorLogs} " +
            "correctness_failure=${ratio(score.correctnessFailure)}"

    private fun ratio(value: Double?): String =
        value?.let { String.format(Locale.ROOT, "%.4f", it) } ?: NO_DATA

    private fun seconds(value: Double?): String =
        value?.let { String.format(Locale.ROOT, "%.3fs", it) } ?: NO_DATA

    private companion object {
        const val TOOL = "analyticsRanking"
        const val NO_DATA = "нет данных"
        const val ERROR_RATE = "error_rate"
        const val LATENCY_P99 = "latency_p99"

        const val SERVICE_FIELD = "service"
        const val SIGNALS_FIELD = "signals"
        const val METRIC_NAME_FIELD = "metricName"
        const val STATS_FIELD = "stats"
        const val LATEST_FIELD = "latest"
        const val COUNT_FIELD = "count"
        const val INVARIANTS_FIELD = "invariants"
        const val FAILURE_RATIO_FIELD = "latestFailureRatio"

        const val MISSING = -1.0

        val HEADER =
            "Рейтинг сервисов рассчитан детерминированно из собранной " +
                "телеметрии: сортировка по error_rate, затем по числу " +
                "ошибок в логах, затем по p99. Порядок менять нельзя, " +
                "первым идёт самый проблемный сервис.\n"

        val ORDER =
            compareByDescending<ServiceScore> { it.errorRate ?: MISSING }
                .thenByDescending(ServiceScore::errorLogs)
                .thenByDescending { it.latencyP99 ?: MISSING }
                .thenBy(ServiceScore::service)
    }
}
