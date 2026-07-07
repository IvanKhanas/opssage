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
import com.opssage.sre.dto.NewError
import com.opssage.sre.dto.RolloutComparisonResult
import com.opssage.sre.dto.RolloutDelta
import com.opssage.sre.dto.RolloutMetrics
import com.opssage.sre.time.TimeWindow
import com.opssage.sre.util.ConfidenceCalculator
import com.opssage.sre.util.Numbers
import reactor.core.publisher.Mono

import kotlin.math.roundToLong

import org.springframework.stereotype.Component

private data class WindowReadings(
    val errorRate: Reading,
    val p95: Reading,
    val p99: Reading,
) {
    fun readings(): List<Reading> = listOf(errorRate, p95, p99)
}

@Component
class RolloutComparisonQuery(
    private val readings: MetricReadings,
    private val templates: PromQlTemplates,
    private val query: QueryProperties,
    private val rolloutErrorDiff: RolloutErrorDiff,
) {

    fun run(request: RolloutQuery): Mono<RolloutComparisonResult> {
        val beforeSpan = request.beforeWindow.coerceAtMost(query.maxWindow)
        val afterSpan = request.afterWindow.coerceAtMost(query.maxWindow)
        val before =
            TimeWindow(
                request.deployTime.minus(beforeSpan),
                request.deployTime,
            )
        val after =
            TimeWindow(
                request.deployTime,
                request.deployTime.plus(afterSpan),
            )
        return Mono
            .zip(
                windowReadings(request, before),
                windowReadings(request, after),
                rolloutErrorDiff.newErrors(
                    request.service,
                    request.namespace,
                    before,
                    after,
                    query.maxNewErrors,
                ),
            ).map { tuple ->
                buildRollout(request, tuple.t1, tuple.t2, tuple.t3)
            }
    }

    private fun windowReadings(
        request: RolloutQuery,
        window: TimeWindow,
    ): Mono<WindowReadings> {
        val scope =
            MetricScope.forWindow(
                request.service,
                request.namespace,
                window,
                query.maxPoints,
                query.minRateWindow.seconds,
            )
        val step = scope.stepSeconds
        return Mono
            .zip(
                readings.reading(window, templates.errorRate(scope), step),
                readings.reading(
                    window,
                    templates.latencyQuantile(scope, PromQlTemplates.P95),
                    step,
                ),
                readings.reading(
                    window,
                    templates.latencyQuantile(scope, PromQlTemplates.P99),
                    step,
                ),
            ).map { tuple ->
                WindowReadings(tuple.t1, tuple.t2, tuple.t3)
            }
    }

    private fun buildRollout(
        request: RolloutQuery,
        before: WindowReadings,
        after: WindowReadings,
        newErrors: List<NewError>,
    ): RolloutComparisonResult {
        val beforeMetrics = rolloutMetrics(before)
        val afterMetrics = rolloutMetrics(after)
        val delta =
            RolloutDelta(
                changePct(before.errorRate.value, after.errorRate.value),
                changePct(before.p95.value, after.p95.value),
                changePct(before.p99.value, after.p99.value),
            )
        val allReadings = before.readings() + after.readings()
        return RolloutComparisonResult(
            service = request.service,
            deployTime = request.deployTime.toString(),
            before = beforeMetrics,
            after = afterMetrics,
            delta = delta,
            newErrors = newErrors,
            narrative = rolloutNarrative(beforeMetrics, afterMetrics, delta),
            confidence =
                ConfidenceCalculator.of(
                    allReadings.count { it.hasData },
                    allReadings.size,
                ),
        )
    }

    private fun rolloutMetrics(reading: WindowReadings): RolloutMetrics =
        RolloutMetrics(
            Numbers.round(reading.errorRate.value),
            Numbers.millis(reading.p95.value),
            Numbers.millis(reading.p99.value),
        )

    private fun rolloutNarrative(
        before: RolloutMetrics,
        after: RolloutMetrics,
        delta: RolloutDelta,
    ): String =
        "Error rate ${before.errorRate} -> ${after.errorRate} " +
            "(${delta.errorRateChange}); p95 ${before.p95Ms}ms -> " +
            "${after.p95Ms}ms (${delta.p95Change}); p99 ${before.p99Ms}ms -> " +
            "${after.p99Ms}ms (${delta.p99Change})."

    private fun changePct(
        before: Double,
        after: Double,
    ): String {
        if (before == 0.0) return if (after == 0.0) "0%" else "new"
        val pct = ((after - before) / before) * Numbers.PERCENT
        val sign = if (pct >= 0) "+" else ""
        return "$sign${pct.roundToLong()}%"
    }
}
