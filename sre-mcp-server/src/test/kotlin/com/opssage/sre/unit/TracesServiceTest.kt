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

import com.opssage.sre.client.VictoriaTracesClient
import com.opssage.sre.model.Confidence
import com.opssage.sre.model.Span
import com.opssage.sre.model.Trace
import com.opssage.sre.traces.TraceAssembler
import com.opssage.sre.traces.TraceQuery
import com.opssage.sre.traces.TracesService
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono

@ExtendWith(MockKExtension::class)
class TracesServiceTest {

    @MockK
    private lateinit var client: VictoriaTracesClient

    private lateinit var service: TracesService

    @BeforeEach
    fun setUp() {
        service =
            TracesService(
                client,
                TraceAssembler(),
                MetricsFixtures.queryProperties(),
            )
    }

    @Test
    fun `summarises user traces with high confidence`() {
        every {
            client.findTraces(any(), any(), any(), any(), any())
        } returns Mono.just(listOf(sampleTrace()))
        val request = TraceQuery("deposit-service", "banking", "u1", 10)

        val result =
            service.userTraces(request, MetricsFixtures.window()).block()!!

        assertThat(result.traces).hasSize(1)
        assertThat(result.traces[0].rootOperation).isEqualTo("GET /pay")
        assertThat(result.traces[0].spanCount).isEqualTo(3)
        assertThat(result.traces[0].serviceCount).isEqualTo(3)
        assertThat(result.traces[0].errorSpanCount).isEqualTo(1)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun `reports low confidence when no traces are found`() {
        every {
            client.findTraces(any(), any(), any(), any(), any())
        } returns Mono.just(emptyList())
        val request = TraceQuery("deposit-service", "banking", "u1", 10)

        val result =
            service.userTraces(request, MetricsFixtures.window()).block()!!

        assertThat(result.traces).isEmpty()
        assertThat(result.confidence).isEqualTo(Confidence.LOW)
    }

    @Test
    fun `summarises service traces without a user id`() {
        every {
            client.findServiceTraces(any(), any(), any(), any())
        } returns Mono.just(listOf(sampleTrace()))

        val result =
            service
                .serviceTraces(
                    "deposit-service",
                    "banking",
                    MetricsFixtures.window(),
                    10,
                ).block()!!

        assertThat(result.service).isEqualTo("deposit-service")
        assertThat(result.traces).hasSize(1)
        assertThat(result.traces[0].errorSpanCount).isEqualTo(1)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun `reports low confidence when no service traces are found`() {
        every {
            client.findServiceTraces(any(), any(), any(), any())
        } returns Mono.just(emptyList())

        val result =
            service
                .serviceTraces(
                    "deposit-service",
                    "banking",
                    MetricsFixtures.window(),
                    10,
                ).block()!!

        assertThat(result.traces).isEmpty()
        assertThat(result.confidence).isEqualTo(Confidence.LOW)
    }

    @Test
    fun `builds an ordered span chain for a trace`() {
        every { client.getTrace("t1") } returns Mono.just(sampleTrace())

        val result = service.traceDetail("t1").block()!!

        assertThat(result.spanCount).isEqualTo(3)
        assertThat(result.serviceCount).isEqualTo(3)
        assertThat(result.errorSpanCount).isEqualTo(1)
        assertThat(result.totalDurationMs).isEqualTo(500.0)
        assertThat(result.spans.map { it.depth }).containsExactly(0, 1, 2)
        assertThat(result.slowestSpan).contains("gateway")
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun `reports low confidence for an unknown trace`() {
        every { client.getTrace("missing") } returns
            Mono.just(Trace("missing", emptyList()))

        val result = service.traceDetail("missing").block()!!

        assertThat(result.spanCount).isEqualTo(0)
        assertThat(result.confidence).isEqualTo(Confidence.LOW)
    }

    private fun sampleTrace(): Trace {
        val spans =
            listOf(
                span(
                    "s1",
                    null,
                    "gateway",
                    "GET /pay",
                    1_000_000,
                    500_000,
                    false,
                ),
                span(
                    "s2",
                    "s1",
                    "deposit",
                    "charge",
                    1_100_000,
                    300_000,
                    false,
                ),
                span("s3", "s2", "core", "debit", 1_200_000, 100_000, true),
            )
        return Trace("t1", spans)
    }

    @Suppress("LongParameterList")
    private fun span(
        id: String,
        parent: String?,
        service: String,
        operation: String,
        start: Long,
        duration: Long,
        error: Boolean,
    ): Span = Span(id, parent, service, operation, start, duration, error)
}
