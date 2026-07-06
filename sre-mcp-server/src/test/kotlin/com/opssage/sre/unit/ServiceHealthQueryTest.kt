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
import com.opssage.sre.metrics.MetricSummarizer
import com.opssage.sre.metrics.PromQlTemplates
import com.opssage.sre.metrics.ServiceHealthQuery
import com.opssage.sre.model.Confidence
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
class ServiceHealthQueryTest {

    @MockK
    private lateinit var client: VictoriaMetricsClient

    private lateinit var health: ServiceHealthQuery

    @BeforeEach
    fun setUp() {
        health =
            ServiceHealthQuery(
                client,
                PromQlTemplates(MetricsFixtures.metricsProperties()),
                MetricSummarizer(),
                MetricsFixtures.queryProperties(),
            )
    }

    @Test
    fun `reports four signals with high confidence when all have data`() {
        every { client.queryRange(any(), any(), any()) } returns
            Mono.just(listOf(MetricsFixtures.risingSeries()))

        val result =
            health
                .run("deposit-service", "banking", MetricsFixtures.window())
                .block()!!

        assertThat(result.service).isEqualTo("deposit-service")
        assertThat(result.signals).hasSize(4)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun `reports low confidence when no series return data`() {
        every { client.queryRange(any(), any(), any()) } returns
            Mono.just(emptyList<MetricSeries>())

        val result =
            health
                .run("deposit-service", "banking", MetricsFixtures.window())
                .block()!!

        assertThat(result.signals).hasSize(4)
        assertThat(result.signals).allMatch { it.stats == null }
        assertThat(result.confidence).isEqualTo(Confidence.LOW)
    }
}
