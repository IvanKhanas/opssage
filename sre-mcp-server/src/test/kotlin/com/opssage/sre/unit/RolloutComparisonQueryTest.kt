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
import com.opssage.sre.dto.NewError
import com.opssage.sre.metrics.MetricReadings
import com.opssage.sre.metrics.MetricSummarizer
import com.opssage.sre.metrics.PromQlTemplates
import com.opssage.sre.metrics.RolloutComparisonQuery
import com.opssage.sre.metrics.RolloutErrorDiff
import com.opssage.sre.metrics.RolloutQuery
import com.opssage.sre.model.Confidence
import com.opssage.sre.time.TimeWindow
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono

import java.time.Duration
import java.time.Instant

@ExtendWith(MockKExtension::class)
class RolloutComparisonQueryTest {

    @MockK
    private lateinit var client: VictoriaMetricsClient

    @MockK
    private lateinit var rolloutErrorDiff: RolloutErrorDiff

    private lateinit var rollout: RolloutComparisonQuery

    private val request =
        RolloutQuery(
            service = "deposit-service",
            namespace = "banking",
            deployTime = Instant.parse("2026-06-27T10:30:00Z"),
            beforeWindow = Duration.ofMinutes(30),
            afterWindow = Duration.ofMinutes(30),
        )

    @BeforeEach
    fun setUp() {
        rollout =
            RolloutComparisonQuery(
                MetricReadings(client, MetricSummarizer()),
                PromQlTemplates(MetricsFixtures.metricsProperties()),
                MetricsFixtures.queryProperties(),
                rolloutErrorDiff,
            )
        every { client.queryRange(any(), any(), any()) } returns
            Mono.just(listOf(MetricsFixtures.risingSeries()))
    }

    @Test
    fun `reports before after metrics with high confidence`() {
        every {
            rolloutErrorDiff.newErrors(any(), any(), any(), any(), any())
        } returns Mono.just(emptyList())

        val result = rollout.run(request).block()!!

        assertThat(result.service).isEqualTo("deposit-service")
        assertThat(result.before.errorRate).isEqualTo(1.5)
        assertThat(result.before.p95Ms).isEqualTo(1500.0)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
        assertThat(result.newErrors).isEmpty()
    }

    @Test
    fun `carries post-deploy error fingerprints into the result`() {
        every {
            rolloutErrorDiff.newErrors(any(), any(), any(), any(), any())
        } returns Mono.just(listOf(NewError("timeout calling <num>", 3)))

        val result = rollout.run(request).block()!!

        assertThat(result.newErrors).hasSize(1)
        assertThat(result.newErrors[0].count).isEqualTo(3)
    }

    @Test
    fun `clamps oversized before and after windows to the max window`() {
        val windows = mutableListOf<TimeWindow>()
        every {
            client.queryRange(any(), capture(windows), any())
        } returns Mono.just(listOf(MetricsFixtures.risingSeries()))
        every {
            rolloutErrorDiff.newErrors(any(), any(), any(), any(), any())
        } returns Mono.just(emptyList())
        val oversized =
            request.copy(
                beforeWindow = Duration.ofDays(365),
                afterWindow = Duration.ofDays(365),
            )

        rollout.run(oversized).block()!!

        val maxWindow = MetricsFixtures.queryProperties().maxWindow
        assertThat(windows).isNotEmpty()
        assertThat(windows.map { it.duration }).allMatch { it <= maxWindow }
    }
}
