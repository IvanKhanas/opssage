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
package com.opssage.sre.contract

import com.opssage.sre.client.VictoriaMetricsClient
import com.opssage.sre.config.CorrectnessProperties
import com.opssage.sre.config.MetricsProperties
import com.opssage.sre.dto.ContractCheck
import com.opssage.sre.dto.ContractStatus
import com.opssage.sre.model.MetricSeries
import com.opssage.sre.time.TimeWindow
import reactor.core.publisher.Mono

import org.springframework.stereotype.Component

@Component
class MetricsContractProbe(
    private val client: VictoriaMetricsClient,
    private val metrics: MetricsProperties,
    private val correctness: CorrectnessProperties,
) {

    fun checks(
        namespace: String,
        window: TimeWindow,
    ): Mono<List<ContractCheck>> =
        Mono
            .zip(
                values(METRIC_NAME_LABEL),
                values(metrics.serviceLabel),
                values(metrics.namespaceLabel),
                values(BUCKET_BOUND_LABEL),
                values(correctness.invariantLabel),
                values(correctness.outcome.label),
                errorSeries(namespace, window),
            ).map { probes ->
                listOf(
                    requestMetric(probes.t1),
                    serviceLabel(probes.t2),
                    namespaceLabel(probes.t3, namespace),
                    latencyBuckets(probes.t1, probes.t4),
                    errorSelector(probes.t7),
                    correctnessMetric(probes.t1, probes.t5, probes.t6),
                )
            }

    private fun values(label: String): Mono<List<String>> =
        client.labelValues(label).onErrorReturn(FAILED)

    private fun errorSeries(
        namespace: String,
        window: TimeWindow,
    ): Mono<List<String>> =
        client
            .queryRange(
                errorProbeQuery(namespace),
                window,
                window.stepSeconds(1),
            ).map { series -> series.map(MetricSeries::name) }
            .onErrorReturn(FAILED)

    private fun errorProbeQuery(namespace: String): String =
        "count(${metrics.requestMetric}{" +
            "${metrics.namespaceLabel}=\"$namespace\",${metrics.errorSelector}})"

    private fun requestMetric(names: List<String>): ContractCheck =
        presence(
            name = "requestMetric",
            expected = "metric ${metrics.requestMetric} exists",
            names = names,
            wanted = metrics.requestMetric,
            impact =
                "getServiceHealth and compareServiceBeforeAfterRollout " +
                    "return no signals at all",
        )

    private fun latencyBuckets(
        names: List<String>,
        buckets: List<String>,
    ): ContractCheck {
        if (names === FAILED || buckets === FAILED) {
            return unreachable("latencyBuckets")
        }
        if (metrics.requestBucketMetric !in names) {
            return ContractCheck(
                name = "latencyBuckets",
                status = ContractStatus.ABSENT,
                expected = "histogram ${metrics.requestBucketMetric}",
                observed = "metric not found",
                impact = "latency_p95 and latency_p99 are never reported",
            )
        }
        if (buckets.isEmpty()) {
            return ContractCheck(
                name = "latencyBuckets",
                status = ContractStatus.MISCONFIGURED,
                expected =
                    "classic histogram with an " +
                        "$BUCKET_BOUND_LABEL label",
                observed =
                    "no $BUCKET_BOUND_LABEL label; native histograms " +
                        "and summaries are not supported",
                impact = "histogram_quantile yields nothing; latency is silent",
            )
        }
        return ok("latencyBuckets", "${buckets.size} bucket bounds")
    }

    private fun serviceLabel(values: List<String>): ContractCheck =
        nonEmpty(
            name = "serviceLabel",
            expected = "label ${metrics.serviceLabel} carries service names",
            values = values,
            impact = "listServices is empty; the agent cannot resolve a target",
        )

    private fun namespaceLabel(
        values: List<String>,
        namespace: String,
    ): ContractCheck {
        if (values === FAILED) return unreachable("namespaceLabel")
        if (values.isEmpty()) {
            return ContractCheck(
                name = "namespaceLabel",
                status = ContractStatus.MISCONFIGURED,
                expected = "label ${metrics.namespaceLabel} exists",
                observed = "label not found",
                impact = "every metric query matches nothing",
            )
        }
        if (namespace !in values) {
            return ContractCheck(
                name = "namespaceLabel",
                status = ContractStatus.UNKNOWN,
                expected = "label ${metrics.namespaceLabel} has $namespace",
                observed = "values: ${values.take(SAMPLE).joinToString()}",
                impact =
                    "this namespace emits no metrics, or is named " +
                        "differently in the metric labels",
            )
        }
        return ok("namespaceLabel", "$namespace present")
    }

    private fun errorSelector(series: List<String>): ContractCheck {
        val expected = "${metrics.errorSelector} matches error series"
        if (series === FAILED) {
            return ContractCheck(
                name = "errorSelector",
                status = ContractStatus.MISCONFIGURED,
                expected = expected,
                observed = "VictoriaMetrics rejected the probe query",
                impact =
                    "error_rate is always 0; a broken service " +
                        "reads healthy",
            )
        }
        if (series.isEmpty()) {
            return ContractCheck(
                name = "errorSelector",
                status = ContractStatus.UNKNOWN,
                expected = expected,
                observed = "query is valid but matched no series in the window",
                impact =
                    "either the namespace had no errors, or the matcher " +
                        "names a label your services do not emit",
            )
        }
        return ok("errorSelector", "matched ${series.size} series")
    }

    private fun correctnessMetric(
        names: List<String>,
        invariants: List<String>,
        outcomes: List<String>,
    ): ContractCheck {
        if (names === FAILED) return unreachable("correctnessMetric")
        val impact =
            "getServiceCorrectness reports no invariants; silent business " +
                "breakage is never detected"
        if (correctness.metric !in names) {
            return ContractCheck(
                name = "correctnessMetric",
                status = ContractStatus.ABSENT,
                expected = "metric ${correctness.metric}",
                observed = "metric not found; your services do not emit it",
                impact = impact,
            )
        }
        if (invariants.isEmpty() ||
            correctness.outcome.failedValue !in outcomes
        ) {
            return ContractCheck(
                name = "correctnessMetric",
                status = ContractStatus.MISCONFIGURED,
                expected =
                    "labels ${correctness.invariantLabel} and " +
                        "${correctness.outcome.label}=" +
                        correctness.outcome.failedValue,
                observed =
                    "invariants: ${invariants.take(SAMPLE)}, " +
                        "outcomes: ${outcomes.take(SAMPLE)}",
                impact = "failure ratios are always 0",
            )
        }
        return ok("correctnessMetric", "${invariants.size} invariants")
    }

    private fun presence(
        name: String,
        expected: String,
        names: List<String>,
        wanted: String,
        impact: String,
    ): ContractCheck {
        if (names === FAILED) return unreachable(name)
        if (wanted !in names) {
            return ContractCheck(
                name = name,
                status = ContractStatus.MISCONFIGURED,
                expected = expected,
                observed = "metric not found among ${names.size} metric names",
                impact = impact,
            )
        }
        return ok(name, "found")
    }

    private fun nonEmpty(
        name: String,
        expected: String,
        values: List<String>,
        impact: String,
    ): ContractCheck {
        if (values === FAILED) return unreachable(name)
        if (values.isEmpty()) {
            return ContractCheck(
                name = name,
                status = ContractStatus.MISCONFIGURED,
                expected = expected,
                observed = "label has no values",
                impact = impact,
            )
        }
        return ok(name, "${values.size} values")
    }

    private fun ok(
        name: String,
        observed: String,
    ): ContractCheck =
        ContractCheck(name, ContractStatus.OK, "satisfied", observed)

    private fun unreachable(name: String): ContractCheck =
        ContractCheck(
            name = name,
            status = ContractStatus.MISCONFIGURED,
            expected = "VictoriaMetrics is reachable at ${metrics.baseUrl}",
            observed = "the query failed",
            impact = "all metric-based tools fail",
        )

    private companion object {
        const val METRIC_NAME_LABEL = "__name__"
        const val BUCKET_BOUND_LABEL = "le"
        const val SAMPLE = 5

        val FAILED: List<String> = listOf("<probe failed>")
    }
}
