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
package com.opssage.sre.unit

import com.opssage.sre.metrics.MetricSummarizer
import com.opssage.sre.model.MetricPoint
import com.opssage.sre.model.MetricSeries
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.time.Instant

class MetricSummarizerTest {

    private val summarizer = MetricSummarizer()
    private val start = Instant.parse("2026-06-27T10:00:00Z")

    private fun series(values: List<Double>): MetricSeries =
        MetricSeries(
            name = "error_rate",
            labels = mapOf("service" to "deposit-service"),
            points =
                values.mapIndexed { index, value ->
                    MetricPoint(start.plusSeconds(index * 60L), value)
                },
        )

    @Test
    fun `computes descriptive statistics over the series`() {
        val summary =
            summarizer.summarize(
                series(listOf(10.0, 20.0, 30.0, 40.0)),
            )
        val stats = summary.stats

        assertThat(summary.datapointCount).isEqualTo(4)
        assertThat(stats).isNotNull
        assertThat(stats!!.first).isEqualTo(10.0)
        assertThat(stats.latest).isEqualTo(40.0)
        assertThat(stats.min).isEqualTo(10.0)
        assertThat(stats.max).isEqualTo(40.0)
        assertThat(stats.mean).isEqualTo(25.0)
        assertThat(stats.delta).isEqualTo(30.0)
        assertThat(stats.windowMinutes).isEqualTo(3.0)
        assertThat(stats.trend).startsWith("increased")
    }

    @Test
    fun `flags a decreasing series in the trend`() {
        val stats = summarizer.summarize(series(listOf(40.0, 10.0))).stats

        assertThat(stats!!.trend).startsWith("decreased")
    }

    @Test
    fun `returns no statistics for an empty series`() {
        val empty =
            MetricSeries(
                name = "error_rate",
                labels = emptyMap(),
                points = emptyList(),
            )

        val summary = summarizer.summarize(empty)

        assertThat(summary.stats).isNull()
        assertThat(summary.datapointCount).isZero()
        assertThat(summary.metricName).isEqualTo("error_rate")
    }
}
