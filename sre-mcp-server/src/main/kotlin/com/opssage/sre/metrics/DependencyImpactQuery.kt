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

import com.opssage.sre.config.QueryProperties
import com.opssage.sre.dto.DependencyImpactResult
import com.opssage.sre.dto.DependencyMetric
import com.opssage.sre.dto.TimeWindowView
import com.opssage.sre.time.TimeWindow
import com.opssage.sre.util.ConfidenceCalculator
import com.opssage.sre.util.Numbers
import com.opssage.sre.util.zipAll
import reactor.core.publisher.Mono

import org.springframework.stereotype.Component

private data class DependencyReading(
    val metric: DependencyMetric,
    val readings: List<Reading>,
)

@Component
class DependencyImpactQuery(
    private val readings: MetricReadings,
    private val templates: PromQlTemplates,
    private val query: QueryProperties,
) {

    val maxDependencies: Int = query.maxDependencies

    fun run(
        request: DependencyQuery,
        window: TimeWindow,
    ): Mono<DependencyImpactResult> {
        val up =
            request.upstream.map {
                depMetric(scope(it, request.namespace, window), window)
            }
        val down =
            request.downstream.map {
                depMetric(scope(it, request.namespace, window), window)
            }
        return Mono.zip(up.zipAll(), down.zipAll()) { upstream, downstream ->
            val upstreamMetrics = upstream.map { it.metric }
            val downstreamMetrics = downstream.map { it.metric }
            val allReadings = (upstream + downstream).flatMap { it.readings }
            DependencyImpactResult(
                service = request.service,
                namespace = request.namespace,
                window = TimeWindowView.of(window),
                upstreamImpact = upstreamMetrics,
                downstreamImpact = downstreamMetrics,
                summary =
                    dependencySummary(
                        request.service,
                        upstreamMetrics,
                        downstreamMetrics,
                    ),
                confidence =
                    ConfidenceCalculator.of(
                        allReadings.count { it.hasData },
                        allReadings.size,
                    ),
            )
        }
    }

    private fun scope(
        service: String,
        namespace: String,
        window: TimeWindow,
    ): MetricScope =
        MetricScope.forWindow(service, namespace, window, query.maxPoints)

    private fun depMetric(
        scope: MetricScope,
        window: TimeWindow,
    ): Mono<DependencyReading> =
        Mono.zip(
            readings.reading(
                window,
                templates.errorRate(scope),
                scope.rateWindowSeconds,
            ),
            readings.reading(
                window,
                templates.latencyQuantile(scope, PromQlTemplates.P99),
                scope.rateWindowSeconds,
            ),
        ) { err, p99 ->
            DependencyReading(
                metric =
                    DependencyMetric(
                        service = scope.service,
                        errorRate = Numbers.round(err.value),
                        latencyP99Ms = Numbers.millis(p99.value),
                        hasData = err.hasData && p99.hasData,
                    ),
                readings = listOf(err, p99),
            )
        }

    private fun dependencySummary(
        service: String,
        upstream: List<DependencyMetric>,
        downstream: List<DependencyMetric>,
    ): String =
        "Dependency impact for $service: ${upstream.size} upstream and " +
            "${downstream.size} downstream services measured."
}
