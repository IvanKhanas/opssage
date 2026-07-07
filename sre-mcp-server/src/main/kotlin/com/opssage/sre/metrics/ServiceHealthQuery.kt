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
package com.opssage.sre.metrics

import com.opssage.sre.client.VictoriaMetricsClient
import com.opssage.sre.config.QueryProperties
import com.opssage.sre.dto.ServiceHealthResult
import com.opssage.sre.dto.TimeWindowView
import com.opssage.sre.model.Confidence
import com.opssage.sre.model.MetricSummary
import com.opssage.sre.time.TimeWindow
import com.opssage.sre.util.ConfidenceCalculator
import com.opssage.sre.util.zipAll
import reactor.core.publisher.Mono

import org.springframework.stereotype.Component

@Component
class ServiceHealthQuery(
    private val client: VictoriaMetricsClient,
    private val templates: PromQlTemplates,
    private val summarizer: MetricSummarizer,
    private val query: QueryProperties,
) {

    fun run(
        service: String,
        namespace: String,
        window: TimeWindow,
    ): Mono<ServiceHealthResult> {
        val scope =
            MetricScope.forWindow(
                service,
                namespace,
                window,
                query.maxPoints,
                query.minRateWindow.seconds,
            )
        val monos =
            queries(scope).map { (name, promql) ->
                client
                    .queryRange(promql, window, scope.stepSeconds)
                    .map { series -> summarizer.summarizeFirst(name, series) }
            }
        return monos.zipAll().map { signals ->
            ServiceHealthResult(
                service = service,
                namespace = namespace,
                window = TimeWindowView.of(window),
                signals = signals,
                summary = summaryLine(service, signals),
                confidence = confidence(signals),
            )
        }
    }

    private fun queries(scope: MetricScope): List<Pair<String, String>> =
        listOf(
            "request_rate" to templates.requestRate(scope),
            "error_rate" to templates.errorRate(scope),
            "latency_p95" to
                templates.latencyQuantile(scope, PromQlTemplates.P95),
            "latency_p99" to
                templates.latencyQuantile(scope, PromQlTemplates.P99),
        )

    private fun confidence(signals: List<MetricSummary>): Confidence =
        ConfidenceCalculator.of(
            signals.count { it.stats != null },
            signals.size,
        )

    private fun summaryLine(
        service: String,
        signals: List<MetricSummary>,
    ): String {
        val withData = signals.count { it.stats != null }
        return "Health summary for $service: $withData of ${signals.size} " +
            "signals returned data."
    }
}
