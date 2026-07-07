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

import com.opssage.sre.client.VictoriaMetricsClient
import com.opssage.sre.config.CorrectnessOutcomeProperties
import com.opssage.sre.config.CorrectnessProperties
import com.opssage.sre.metrics.CorrectnessQueryConfig
import com.opssage.sre.metrics.MetricSummarizer
import com.opssage.sre.metrics.ServiceCorrectnessQuery
import com.opssage.sre.model.Confidence
import com.opssage.sre.model.MetricPoint
import com.opssage.sre.model.MetricSeries
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono

@ExtendWith(MockKExtension::class)
class ServiceCorrectnessQueryTest {

    @MockK
    private lateinit var client: VictoriaMetricsClient

    private lateinit var config: CorrectnessQueryConfig
    private lateinit var query: ServiceCorrectnessQuery

    @BeforeEach
    fun setUp() {
        config =
            CorrectnessQueryConfig(
                correctnessProperties(),
                MetricsFixtures.metricsProperties(),
                MetricsFixtures.queryProperties(),
            )
        query = ServiceCorrectnessQuery(client, MetricSummarizer(), config)
    }

    @Test
    fun `maps every invariant series with high confidence`() {
        every { client.queryRange(any(), any(), any()) } returns
            Mono.just(
                listOf(
                    invariantSeries("currency_matches", 0.0, 0.0),
                    invariantSeries("balance_preserved", 0.0, 0.25),
                ),
            )

        val result =
            query
                .run("deposit-service", "banking", MetricsFixtures.window())
                .block()!!

        assertThat(result.invariants).hasSize(2)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
        val failing =
            result.invariants.first { it.invariant == "balance_preserved" }
        assertThat(failing.latestFailureRatio).isEqualTo(0.25)
        assertThat(failing.peakFailureRatio).isEqualTo(0.25)
        val healthy =
            result.invariants.first { it.invariant == "currency_matches" }
        assertThat(healthy.latestFailureRatio).isEqualTo(0.0)
    }

    @Test
    fun `reports low confidence when no telemetry is observed`() {
        every { client.queryRange(any(), any(), any()) } returns
            Mono.just(emptyList<MetricSeries>())

        val result =
            query
                .run("deposit-service", "banking", MetricsFixtures.window())
                .block()!!

        assertThat(result.invariants).isEmpty()
        assertThat(result.confidence).isEqualTo(Confidence.LOW)
    }

    @Test
    fun `skips series without an invariant label`() {
        every { client.queryRange(any(), any(), any()) } returns
            Mono.just(
                listOf(
                    MetricSeries("value", emptyMap(), twoPoints(0.0, 0.1)),
                    invariantSeries("balance_preserved", 0.0, 0.1),
                ),
            )

        val result =
            query
                .run("deposit-service", "banking", MetricsFixtures.window())
                .block()!!

        assertThat(result.invariants).hasSize(1)
        assertThat(result.invariants[0].invariant)
            .isEqualTo("balance_preserved")
    }

    @Test
    fun `builds a bounded failure-ratio promql with a zero fallback`() {
        val scope =
            config.scope("deposit-service", "banking", MetricsFixtures.window())
        val rate = "${scope.rateWindowSeconds}s"
        val labels = "service=\"deposit-service\",namespace=\"banking\""
        val total =
            "sum by (invariant) " +
                "(rate(service_correctness_checks_total{$labels}[$rate]))"
        val failures =
            "sum by (invariant) (rate(service_correctness_checks_total" +
                "{$labels,outcome=\"FAIL\"}[$rate]))"

        assertThat(config.promql(scope))
            .isEqualTo("(($failures) or ($total * 0)) / ($total)")
    }

    private fun invariantSeries(
        invariant: String,
        first: Double,
        last: Double,
    ): MetricSeries =
        MetricSeries(
            "value",
            mapOf("invariant" to invariant),
            twoPoints(first, last),
        )

    private fun twoPoints(
        first: Double,
        last: Double,
    ): List<MetricPoint> =
        listOf(
            MetricPoint(MetricsFixtures.window().from, first),
            MetricPoint(MetricsFixtures.window().to, last),
        )

    private fun correctnessProperties(): CorrectnessProperties =
        CorrectnessProperties(
            metric = "service_correctness_checks_total",
            invariantLabel = "invariant",
            outcome =
                CorrectnessOutcomeProperties(
                    label = "outcome",
                    failedValue = "FAIL",
                ),
        )
}
