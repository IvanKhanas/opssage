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
import com.opssage.sre.metrics.DependencyImpactQuery
import com.opssage.sre.metrics.DependencyQuery
import com.opssage.sre.metrics.MetricReadings
import com.opssage.sre.metrics.MetricSummarizer
import com.opssage.sre.metrics.PromQlTemplates
import com.opssage.sre.model.Confidence
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono

@ExtendWith(MockKExtension::class)
class DependencyImpactQueryTest {

    @MockK
    private lateinit var client: VictoriaMetricsClient

    private lateinit var dependency: DependencyImpactQuery

    @BeforeEach
    fun setUp() {
        dependency =
            DependencyImpactQuery(
                MetricReadings(client, MetricSummarizer()),
                PromQlTemplates(MetricsFixtures.metricsProperties()),
                MetricsFixtures.queryProperties(),
            )
    }

    @Test
    fun `measures upstream and downstream dependency metrics`() {
        every { client.queryRange(any(), any(), any()) } returns
            Mono.just(listOf(MetricsFixtures.risingSeries()))
        val request =
            DependencyQuery(
                service = "deposit-service",
                namespace = "banking",
                upstream = listOf("mobile-api"),
                downstream = listOf("core-banking-adapter"),
            )

        val result =
            dependency.run(request, MetricsFixtures.window()).block()!!

        assertThat(result.upstreamImpact).hasSize(1)
        assertThat(result.upstreamImpact[0].service).isEqualTo("mobile-api")
        assertThat(result.downstreamImpact).hasSize(1)
        assertThat(result.downstreamImpact[0].errorRate).isEqualTo(1.5)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun `reports lower confidence when a dependency signal is missing`() {
        every { client.queryRange(any(), any(), any()) } returns
            Mono.just(emptyList())
        val request =
            DependencyQuery(
                service = "deposit-service",
                namespace = "banking",
                upstream = listOf("mobile-api"),
                downstream = emptyList(),
            )

        val result =
            dependency.run(request, MetricsFixtures.window()).block()!!

        assertThat(result.upstreamImpact[0].hasData).isFalse()
        assertThat(result.confidence).isEqualTo(Confidence.LOW)
    }

    @Test
    fun `reports low confidence when no dependencies are provided`() {
        val request =
            DependencyQuery(
                service = "deposit-service",
                namespace = "banking",
                upstream = emptyList(),
                downstream = emptyList(),
            )

        val result =
            dependency.run(request, MetricsFixtures.window()).block()!!

        assertThat(result.upstreamImpact).isEmpty()
        assertThat(result.downstreamImpact).isEmpty()
        assertThat(result.confidence).isEqualTo(Confidence.LOW)
    }

    @Test
    fun `flags no data when only one dependency signal is present`() {
        every {
            client.queryRange(
                match { it.contains("histogram_quantile") },
                any(),
                any(),
            )
        } returns Mono.just(emptyList())
        every {
            client.queryRange(
                match { !it.contains("histogram_quantile") },
                any(),
                any(),
            )
        } returns Mono.just(listOf(MetricsFixtures.risingSeries()))
        val request =
            DependencyQuery(
                service = "deposit-service",
                namespace = "banking",
                upstream = listOf("mobile-api"),
                downstream = emptyList(),
            )

        val result =
            dependency.run(request, MetricsFixtures.window()).block()!!

        assertThat(result.upstreamImpact[0].hasData).isFalse()
        assertThat(result.confidence).isEqualTo(Confidence.MEDIUM)
    }
}
