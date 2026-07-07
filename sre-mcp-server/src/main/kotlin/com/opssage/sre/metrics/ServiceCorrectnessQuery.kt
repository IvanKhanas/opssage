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
import com.opssage.sre.dto.InvariantRatio
import com.opssage.sre.dto.ServiceCorrectnessResult
import com.opssage.sre.dto.TimeWindowView
import com.opssage.sre.model.Confidence
import com.opssage.sre.model.MetricSeries
import com.opssage.sre.time.TimeWindow
import reactor.core.publisher.Mono

import org.springframework.stereotype.Component

@Component
class ServiceCorrectnessQuery(
    private val client: VictoriaMetricsClient,
    private val summarizer: MetricSummarizer,
    private val config: CorrectnessQueryConfig,
) {

    fun run(
        service: String,
        namespace: String,
        window: TimeWindow,
    ): Mono<ServiceCorrectnessResult> {
        val scope = config.scope(service, namespace, window)
        return client
            .queryRange(config.promql(scope), window, scope.stepSeconds)
            .map { series ->
                val invariants = toInvariantRatios(series)
                ServiceCorrectnessResult(
                    service,
                    namespace,
                    TimeWindowView.of(window),
                    invariants,
                    "Found ${invariants.size} correctness invariants for " +
                        "$service.",
                    if (invariants.isEmpty()) {
                        Confidence.LOW
                    } else {
                        Confidence.HIGH
                    },
                )
            }
    }

    private fun toInvariantRatios(
        series: List<MetricSeries>,
    ): List<InvariantRatio> =
        series.mapNotNull { metric ->
            val invariant =
                metric.labels[config.invariantLabel]
                    ?: return@mapNotNull null
            val stats =
                summarizer.summarize(metric).stats
                    ?: return@mapNotNull null
            InvariantRatio(
                invariant,
                stats.latest,
                stats.max,
                stats.trend,
            )
        }
}
