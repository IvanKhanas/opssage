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
package com.opssage.sre.client

import com.opssage.sre.model.MetricPoint
import com.opssage.sre.model.MetricSeries
import com.opssage.sre.time.TimeWindow
import io.github.oshai.kotlinlogging.KotlinLogging
import reactor.core.publisher.Mono

import java.time.Instant

import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

private val log = KotlinLogging.logger {}

@Component
class VictoriaMetricsClient(
    private val victoriaMetricsWebClient: WebClient,
) {

    fun queryRange(
        promql: String,
        window: TimeWindow,
        stepSeconds: Long,
    ): Mono<List<MetricSeries>> =
        victoriaMetricsWebClient
            .get()
            .uri { builder ->
                builder
                    .path("/api/v1/query_range")
                    .queryParam("query", "{query}")
                    .queryParam("start", window.from.epochSecond)
                    .queryParam("end", window.to.epochSecond)
                    .queryParam("step", "${stepSeconds}s")
                    .build(mapOf("query" to promql))
            }.retrieve()
            .bodyToMono<PrometheusResponse>()
            .map { response ->
                response.data
                    ?.result
                    .orEmpty()
                    .map(::toSeries)
            }.doOnError { error ->
                log.atWarn {
                    message = "VictoriaMetrics query_range failed"
                    payload = mapOf("query" to promql)
                    cause = error
                }
            }

    fun labelValues(label: String): Mono<List<String>> =
        victoriaMetricsWebClient
            .get()
            .uri { builder ->
                builder
                    .path("/api/v1/label/{label}/values")
                    .build(mapOf("label" to label))
            }.retrieve()
            .bodyToMono<PrometheusLabelValuesResponse>()
            .map { it.data }
            .doOnError { error ->
                log.atWarn {
                    message = "VictoriaMetrics label values query failed"
                    payload = mapOf("label" to label)
                    cause = error
                }
            }

    private fun toSeries(series: PrometheusSeries): MetricSeries {
        val name = series.metric["__name__"].orEmpty()
        val labels = series.metric.filterKeys { it != "__name__" }
        return MetricSeries(name, labels, series.values.mapNotNull(::toPoint))
    }

    private fun toPoint(pair: List<String>): MetricPoint? {
        if (pair.size < 2) return null
        val timestamp = pair[0].toDoubleOrNull() ?: return null
        val value = pair[1].toDoubleOrNull() ?: return null
        if (!value.isFinite()) return null
        return MetricPoint(Instant.ofEpochSecond(timestamp.toLong()), value)
    }
}
