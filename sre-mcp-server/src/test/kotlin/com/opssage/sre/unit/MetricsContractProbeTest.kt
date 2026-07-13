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
import com.opssage.sre.contract.MetricsContractProbe
import com.opssage.sre.dto.ContractCheck
import com.opssage.sre.dto.ContractStatus
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
class MetricsContractProbeTest {

    @MockK
    lateinit var client: VictoriaMetricsClient

    private lateinit var probe: MetricsContractProbe

    @BeforeEach
    fun setUp() {
        probe =
            MetricsContractProbe(
                client,
                MetricsFixtures.metricsProperties(),
                CorrectnessProperties(
                    metric = "service_correctness_checks_total",
                    invariantLabel = "invariant",
                    outcome =
                        CorrectnessOutcomeProperties(
                            label = "outcome",
                            failedValue = "FAIL",
                        ),
                ),
            )
        healthyCluster()
    }

    @Test
    fun `reports a healthy cluster as fully satisfied`() {
        val checks = run()

        assertThat(checks).allSatisfy { check ->
            assertThat(check.status).isEqualTo(ContractStatus.OK)
        }
    }

    @Test
    fun `calls a missing correctness metric absent, not misconfigured`() {
        every { client.labelValues(NAME_LABEL) } returns
            Mono.just(listOf(REQUEST_METRIC, BUCKET_METRIC))

        assertThat(status(run(), "correctnessMetric"))
            .isEqualTo(ContractStatus.ABSENT)
    }

    @Test
    fun `calls a rejected error matcher misconfigured`() {
        every { client.queryRange(any(), any(), any()) } returns
            Mono.error(IllegalStateException("400 bad request"))

        val check = check(run(), "errorSelector")

        assertThat(check.status).isEqualTo(ContractStatus.MISCONFIGURED)
        assertThat(check.impact).contains("a broken service reads healthy")
    }

    @Test
    fun `cannot tell an error-free window from a wrong matcher`() {
        every { client.queryRange(any(), any(), any()) } returns
            Mono.just(emptyList())

        assertThat(status(run(), "errorSelector"))
            .isEqualTo(ContractStatus.UNKNOWN)
    }

    @Test
    fun `flags a histogram without bucket bounds as misconfigured`() {
        every { client.labelValues(BUCKET_BOUND_LABEL) } returns
            Mono.just(emptyList())

        val check = check(run(), "latencyBuckets")

        assertThat(check.status).isEqualTo(ContractStatus.MISCONFIGURED)
        assertThat(check.observed).contains("native histograms")
    }

    @Test
    fun `reports a namespace absent from the metric labels as unknown`() {
        every { client.labelValues("namespace") } returns
            Mono.just(listOf("other"))

        assertThat(status(run(), "namespaceLabel"))
            .isEqualTo(ContractStatus.UNKNOWN)
    }

    @Test
    fun `treats an unreachable metrics backend as misconfigured everywhere`() {
        every { client.labelValues(any()) } returns
            Mono.error(IllegalStateException("connection refused"))

        val checks = run()

        assertThat(checks.filter { it.name != "errorSelector" })
            .allSatisfy { check ->
                assertThat(check.status).isEqualTo(ContractStatus.MISCONFIGURED)
            }
    }

    private fun healthyCluster() {
        every { client.labelValues(NAME_LABEL) } returns
            Mono.just(
                listOf(REQUEST_METRIC, BUCKET_METRIC, CORRECTNESS_METRIC),
            )
        every { client.labelValues("service") } returns
            Mono.just(listOf("checkout"))
        every { client.labelValues("namespace") } returns
            Mono.just(listOf("prod"))
        every { client.labelValues(BUCKET_BOUND_LABEL) } returns
            Mono.just(listOf("0.1", "+Inf"))
        every { client.labelValues("invariant") } returns
            Mono.just(listOf("balance_preserved"))
        every { client.labelValues("outcome") } returns
            Mono.just(listOf("PASS", "FAIL"))
        every { client.queryRange(any(), any(), any()) } returns
            Mono.just(
                listOf(MetricSeries(REQUEST_METRIC, emptyMap(), emptyList())),
            )
    }

    private fun run(): List<ContractCheck> =
        probe.checks("prod", MetricsFixtures.window()).block()!!

    private fun check(
        checks: List<ContractCheck>,
        name: String,
    ): ContractCheck = checks.single { it.name == name }

    private fun status(
        checks: List<ContractCheck>,
        name: String,
    ): ContractStatus = check(checks, name).status

    private companion object {
        const val NAME_LABEL = "__name__"
        const val BUCKET_BOUND_LABEL = "le"
        const val REQUEST_METRIC = "http_server_requests_seconds_count"
        const val BUCKET_METRIC = "http_server_requests_seconds_bucket"
        const val CORRECTNESS_METRIC = "service_correctness_checks_total"
    }
}
