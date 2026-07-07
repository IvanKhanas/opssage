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

import com.opssage.sre.model.MetricSeries
import com.opssage.sre.model.MetricStats
import com.opssage.sre.model.MetricSummary
import com.opssage.sre.util.Numbers

import kotlin.math.abs

import org.springframework.stereotype.Component

@Component
class MetricSummarizer {

    fun summarizeAll(series: List<MetricSeries>): List<MetricSummary> =
        series.map(::summarize)

    fun summarizeFirst(
        name: String,
        series: List<MetricSeries>,
    ): MetricSummary =
        summarize(
            MetricSeries(
                name,
                emptyMap(),
                series.firstOrNull()?.points.orEmpty(),
            ),
        )

    fun summarize(series: MetricSeries): MetricSummary {
        val stats = computeStats(series)
        return MetricSummary(
            metricName = series.name,
            labels = series.labels,
            datapointCount = series.points.size,
            stats = stats,
        )
    }

    private fun computeStats(series: MetricSeries): MetricStats? {
        val points = series.points
        if (points.isEmpty()) return null

        val values = points.map { it.value }
        val first = points.first()
        val latest = points.last()
        val windowSeconds =
            latest.timestamp.epochSecond - first.timestamp.epochSecond

        return MetricStats(
            first = round(first.value),
            latest = round(latest.value),
            min = round(values.min()),
            max = round(values.max()),
            mean = round(values.average()),
            p95 = round(percentile(values.sorted(), P95_RANK)),
            trend = trend(first.value, latest.value),
            delta = round(latest.value - first.value),
            windowMinutes =
                if (windowSeconds >
                    0
                ) {
                    round(windowSeconds / SECONDS_PER_MIN)
                } else {
                    0.0
                },
        )
    }

    private fun percentile(
        sorted: List<Double>,
        pct: Double,
    ): Double {
        if (sorted.isEmpty()) return 0.0
        if (sorted.size == 1) return sorted[0]
        val rank = (pct / Numbers.PERCENT) * (sorted.size - 1)
        val lo = rank.toInt()
        val hi = minOf(lo + 1, sorted.size - 1)
        val weight = rank - lo
        return sorted[lo] * (1 - weight) + sorted[hi] * weight
    }

    private fun trend(
        first: Double,
        latest: Double,
    ): String =
        when {
            latest > first -> "increased ${change(first, latest)}"
            latest < first -> "decreased ${change(first, latest)}"
            else -> "flat"
        }

    private fun change(
        start: Double,
        end: Double,
    ): String {
        val delta = end - start
        if (start == 0.0) {
            return if (end == 0.0) "0" else "${signed(delta)} from zero"
        }
        val pct = abs(delta / start) * Numbers.PERCENT
        return "${round(pct)}% (${signed(delta)})"
    }

    private fun signed(value: Double): String {
        val rounded = round(value)
        return if (rounded >= 0) "+$rounded" else "$rounded"
    }

    private fun round(value: Double): Double = Numbers.round(value)

    private companion object {
        const val P95_RANK = 95.0
        const val SECONDS_PER_MIN = 60.0
    }
}
