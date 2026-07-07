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

import com.opssage.sre.alert.AlertContextQuery
import com.opssage.sre.dto.KubernetesEventsResult
import com.opssage.sre.dto.ServiceHealthResult
import com.opssage.sre.dto.TimeWindowView
import com.opssage.sre.dto.TopLogErrorsResult
import com.opssage.sre.kubernetes.KubernetesService
import com.opssage.sre.logs.LogQuery
import com.opssage.sre.logs.LogsService
import com.opssage.sre.metrics.ServiceHealthQuery
import com.opssage.sre.model.Confidence
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono

@ExtendWith(MockKExtension::class)
class AlertContextQueryTest {

    @MockK
    private lateinit var health: ServiceHealthQuery

    @MockK
    private lateinit var logs: LogsService

    @MockK
    private lateinit var kubernetes: KubernetesService

    private lateinit var alertContext: AlertContextQuery

    @BeforeEach
    fun setUp() {
        alertContext =
            AlertContextQuery(
                health,
                logs,
                kubernetes,
                MetricsFixtures.queryProperties(),
            )
    }

    @Test
    fun `aggregates the three sources and takes the lowest confidence`() {
        val logQuery = slot<LogQuery>()
        every { health.run(any(), any(), any()) } returns
            Mono.just(healthResult(Confidence.HIGH))
        every { logs.topLogErrors(capture(logQuery), any()) } returns
            Mono.just(errorsResult(Confidence.MEDIUM))
        every { kubernetes.serviceEvents(any(), any()) } returns
            Mono.just(kubernetesResult(Confidence.LOW))

        val result =
            alertContext
                .run("deposit-service", "banking", MetricsFixtures.window())
                .block()!!

        assertThat(result.service).isEqualTo("deposit-service")
        assertThat(result.health.confidence).isEqualTo(Confidence.HIGH)
        assertThat(result.topErrors.confidence).isEqualTo(Confidence.MEDIUM)
        assertThat(result.kubernetes.confidence).isEqualTo(Confidence.LOW)
        assertThat(result.confidence).isEqualTo(Confidence.LOW)
        assertThat(logQuery.captured.limit).isEqualTo(5)
    }

    @Test
    fun `keeps partial context when one source fails`() {
        every { health.run(any(), any(), any()) } returns
            Mono.just(healthResult(Confidence.HIGH))
        every { logs.topLogErrors(any(), any()) } returns
            Mono.error(IllegalStateException("logs unavailable"))
        every { kubernetes.serviceEvents(any(), any()) } returns
            Mono.just(kubernetesResult(Confidence.HIGH))

        val result =
            alertContext
                .run("deposit-service", "banking", MetricsFixtures.window())
                .block()!!

        assertThat(result.health.summary).isEqualTo("health")
        assertThat(result.topErrors.summary)
            .isEqualTo("Log source unavailable.")
        assertThat(result.kubernetes.summary).isEqualTo("kubernetes")
        assertThat(result.confidence).isEqualTo(Confidence.LOW)
    }

    private fun healthResult(confidence: Confidence): ServiceHealthResult =
        ServiceHealthResult(
            service = "deposit-service",
            namespace = "banking",
            window = window(),
            signals = emptyList(),
            summary = "health",
            confidence = confidence,
        )

    private fun errorsResult(confidence: Confidence): TopLogErrorsResult =
        TopLogErrorsResult(
            service = "deposit-service",
            namespace = "banking",
            window = window(),
            topErrors = emptyList(),
            summary = "errors",
            confidence = confidence,
        )

    private fun kubernetesResult(
        confidence: Confidence,
    ): KubernetesEventsResult =
        KubernetesEventsResult(
            service = "deposit-service",
            namespace = "banking",
            pods = emptyList(),
            events = emptyList(),
            warningCount = 0,
            notReadyPodCount = 0,
            summary = "kubernetes",
            confidence = confidence,
        )

    private fun window(): TimeWindowView = TimeWindowView("from", "to")
}
