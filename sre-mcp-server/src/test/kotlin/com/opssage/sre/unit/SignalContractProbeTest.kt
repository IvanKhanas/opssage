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

import com.opssage.sre.client.KubernetesClient
import com.opssage.sre.client.VictoriaLogsClient
import com.opssage.sre.client.VictoriaTracesClient
import com.opssage.sre.config.KubernetesProperties
import com.opssage.sre.config.LogsProperties
import com.opssage.sre.contract.SignalContractClients
import com.opssage.sre.contract.SignalContractProbe
import com.opssage.sre.dto.ContractCheck
import com.opssage.sre.dto.ContractStatus
import com.opssage.sre.time.TimeWindow
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono

import java.time.Instant

@ExtendWith(MockKExtension::class)
class SignalContractProbeTest {

    @MockK
    lateinit var logsClient: VictoriaLogsClient

    @MockK
    lateinit var tracesClient: VictoriaTracesClient

    @MockK
    lateinit var kubernetesClient: KubernetesClient

    private lateinit var probe: SignalContractProbe

    @BeforeEach
    fun setUp() {
        probe =
            SignalContractProbe(
                SignalContractClients(
                    logsClient,
                    tracesClient,
                    kubernetesClient,
                ),
                logsProperties(),
                kubernetesProperties(),
            )
        healthyCluster()
    }

    @Test
    fun `reports a healthy cluster as fully satisfied`() {
        assertThat(run()).allSatisfy { check ->
            assertThat(check.status).isEqualTo(ContractStatus.OK)
        }
    }

    @Test
    fun `treats an unreachable stack as misconfigured everywhere it can`() {
        every { logsClient.probe(any(), any(), any()) } returns
            Mono.error(IllegalStateException("logs down"))
        every { tracesClient.services() } returns
            Mono.error(IllegalStateException("jaeger down"))
        every { kubernetesClient.countPods(any()) } returns
            Mono.error(IllegalStateException("api down"))

        val checks = run()

        assertThat(status(checks, "logStream"))
            .isEqualTo(ContractStatus.MISCONFIGURED)
        assertThat(status(checks, "errorLevels"))
            .isEqualTo(ContractStatus.UNKNOWN)
        assertThat(status(checks, "traceBackend"))
            .isEqualTo(ContractStatus.MISCONFIGURED)
        assertThat(status(checks, "kubernetesApi"))
            .isEqualTo(ContractStatus.MISCONFIGURED)
    }

    @Test
    fun `reports an empty window as unknown or absent, not misconfigured`() {
        every { logsClient.probe(any(), any(), false) } returns Mono.just(0)
        every { tracesClient.services() } returns Mono.just(emptyList())
        every { kubernetesClient.countPods(any()) } returns Mono.just(0)

        val checks = run()

        assertThat(status(checks, "logStream"))
            .isEqualTo(ContractStatus.UNKNOWN)
        assertThat(status(checks, "errorLevels"))
            .isEqualTo(ContractStatus.UNKNOWN)
        assertThat(status(checks, "traceBackend"))
            .isEqualTo(ContractStatus.ABSENT)
        assertThat(status(checks, "kubernetesApi"))
            .isEqualTo(ContractStatus.UNKNOWN)
    }

    @Test
    fun `flags logs without error levels and pods without known labels`() {
        every { logsClient.probe(any(), any(), true) } returns Mono.just(0)
        every { kubernetesClient.firstMatchingLabel(any()) } returns
            Mono.just("")

        val checks = run()

        val errorLevels = check(checks, "errorLevels")
        assertThat(errorLevels.status).isEqualTo(ContractStatus.UNKNOWN)
        assertThat(errorLevels.observed).contains("none carry these levels")

        val kubernetes = check(checks, "kubernetesApi")
        assertThat(kubernetes.status).isEqualTo(ContractStatus.MISCONFIGURED)
        assertThat(kubernetes.observed).contains("none carry any of")
    }

    private fun healthyCluster() {
        every { logsClient.probe(any(), any(), false) } returns Mono.just(120)
        every { logsClient.probe(any(), any(), true) } returns Mono.just(12)
        every { tracesClient.services() } returns
            Mono.just(listOf("payment-service"))
        every { kubernetesClient.countPods(any()) } returns Mono.just(4)
        every { kubernetesClient.firstMatchingLabel(any()) } returns
            Mono.just("app")
    }

    private fun run(): List<ContractCheck> =
        probe.checks("banking", WINDOW).block() ?: emptyList()

    private fun check(
        checks: List<ContractCheck>,
        name: String,
    ): ContractCheck = checks.first { it.name == name }

    private fun status(
        checks: List<ContractCheck>,
        name: String,
    ): ContractStatus = check(checks, name).status

    private fun logsProperties(): LogsProperties =
        LogsProperties(
            baseUrl = "http://logs",
            serviceField = "service",
            namespaceField = "namespace",
            levelField = "level",
            errorLevels = listOf("ERROR"),
            messageField = "_msg",
            traceField = "trace_id",
            timeField = "_time",
            maxSamples = 100,
            maxScanSamples = 1_000,
        )

    private fun kubernetesProperties(): KubernetesProperties =
        KubernetesProperties(
            baseUrl = "http://k8s",
            token = "token",
            tokenPath = null,
            appLabels = listOf("app.kubernetes.io/name", "app"),
            caCertPath = null,
        )

    private companion object {
        val WINDOW =
            TimeWindow(
                Instant.parse("2026-07-09T10:00:00Z"),
                Instant.parse("2026-07-09T12:00:00Z"),
            )
    }
}
