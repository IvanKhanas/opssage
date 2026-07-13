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
package com.opssage.sre.contract

import com.opssage.sre.client.KubernetesClient
import com.opssage.sre.client.VictoriaLogsClient
import com.opssage.sre.client.VictoriaTracesClient
import com.opssage.sre.config.KubernetesProperties
import com.opssage.sre.config.LogsProperties
import com.opssage.sre.dto.ContractCheck
import com.opssage.sre.dto.ContractStatus
import com.opssage.sre.time.TimeWindow
import reactor.core.publisher.Mono

import org.springframework.stereotype.Component

private data class SignalProbes(
    val anyLogs: Int,
    val errorLogs: Int,
    val traceServices: Int,
    val pods: Int,
    val podLabel: String,
)

@Component
class SignalContractProbe(
    private val clients: SignalContractClients,
    private val logs: LogsProperties,
    private val kubernetes: KubernetesProperties,
) {

    fun checks(
        namespace: String,
        window: TimeWindow,
    ): Mono<List<ContractCheck>> =
        Mono
            .zip(
                count(clients.logs.probe(namespace, window, false)),
                count(clients.logs.probe(namespace, window, true)),
                count(clients.traces.services().map(List<String>::size)),
                count(clients.kubernetes.countPods(namespace)),
                clients.kubernetes
                    .firstMatchingLabel(namespace)
                    .onErrorReturn(""),
            ).map { probes ->
                report(
                    SignalProbes(
                        probes.t1,
                        probes.t2,
                        probes.t3,
                        probes.t4,
                        probes.t5,
                    ),
                )
            }

    private fun count(probe: Mono<Int>): Mono<Int> = probe.onErrorReturn(FAILED)

    private fun report(probes: SignalProbes): List<ContractCheck> =
        listOf(
            logStream(probes),
            errorLevels(probes),
            traceBackend(probes.traceServices),
            kubernetesApi(probes),
        )

    private fun logStream(probes: SignalProbes): ContractCheck {
        val expected = "field ${logs.namespaceField} selects log lines"
        if (probes.anyLogs == FAILED) {
            return ContractCheck(
                name = "logStream",
                status = ContractStatus.MISCONFIGURED,
                expected = expected,
                observed = "VictoriaLogs rejected the probe query",
                impact = "findTopLogErrors and findLogErrorsByText fail",
            )
        }
        if (probes.anyLogs == 0) {
            return ContractCheck(
                name = "logStream",
                status = ContractStatus.UNKNOWN,
                expected = expected,
                observed = "no log lines at all in the window",
                impact =
                    "either nothing logged, or the namespace field is " +
                        "named differently in your log records",
            )
        }
        return ok("logStream", "log lines present")
    }

    private fun errorLevels(probes: SignalProbes): ContractCheck {
        val configured = logs.errorLevels.joinToString()
        if (probes.errorLogs == FAILED || probes.anyLogs <= 0) {
            return ContractCheck(
                name = "errorLevels",
                status = ContractStatus.UNKNOWN,
                expected = "field ${logs.levelField} in [$configured]",
                observed = "cannot tell: the namespace produced no log lines",
                impact = "error aggregation is unverified",
            )
        }
        if (probes.errorLogs == 0) {
            return ContractCheck(
                name = "errorLevels",
                status = ContractStatus.UNKNOWN,
                expected = "field ${logs.levelField} in [$configured]",
                observed = "logs exist, but none carry these levels",
                impact =
                    "either the namespace logged no errors, or your " +
                        "services spell the level differently, for example " +
                        "lowercase or a severity number",
            )
        }
        return ok("errorLevels", "error lines match [$configured]")
    }

    private fun traceBackend(services: Int): ContractCheck {
        val expected = "the Jaeger select API lists services"
        if (services == FAILED) {
            return ContractCheck(
                name = "traceBackend",
                status = ContractStatus.MISCONFIGURED,
                expected = expected,
                observed = "the Jaeger services endpoint failed",
                impact =
                    "findServiceTraces, findUserRelatedTraces and " +
                        "summarizeTrace all fail; HIGH confidence stays " +
                        "unreachable because it needs trace confirmation",
            )
        }
        if (services == 0) {
            return ContractCheck(
                name = "traceBackend",
                status = ContractStatus.ABSENT,
                expected = expected,
                observed = "the trace backend knows no services",
                impact = "no trace evidence; reports cap at MEDIUM confidence",
            )
        }
        return ok("traceBackend", "$services services known to the backend")
    }

    private fun kubernetesApi(probes: SignalProbes): ContractCheck {
        val pods = probes.pods
        val labels = kubernetes.podSelectors().joinToString()
        if (pods == FAILED) {
            return ContractCheck(
                name = "kubernetesApi",
                status = ContractStatus.MISCONFIGURED,
                expected = "the Kubernetes API answers for this namespace",
                observed =
                    "the pods request failed; check the token, its " +
                        "expiry, the CA certificate and the RBAC binding",
                impact =
                    "getKubernetesServiceEvents fails; rollout checks " +
                        "lose pod state and cluster events",
            )
        }
        if (pods == 0) {
            return ContractCheck(
                name = "kubernetesApi",
                status = ContractStatus.UNKNOWN,
                expected = "the namespace holds pods labelled by [$labels]",
                observed = "the API answered, but the namespace has no pods",
                impact = "kubernetes evidence will be empty",
            )
        }
        if (probes.podLabel.isBlank()) {
            return ContractCheck(
                name = "kubernetesApi",
                status = ContractStatus.MISCONFIGURED,
                expected = "pods carry one of the labels [$labels]",
                observed =
                    "the namespace holds pods, but none carry any of " +
                        "these label keys",
                impact =
                    "getKubernetesServiceEvents finds no pods for any " +
                        "service; rollout checks lose pod state and events",
            )
        }
        return ok("kubernetesApi", "pods matched by ${probes.podLabel}")
    }

    private fun ok(
        name: String,
        observed: String,
    ): ContractCheck =
        ContractCheck(name, ContractStatus.OK, "satisfied", observed)

    private companion object {
        const val FAILED = -1
    }
}

@Component
class SignalContractClients(
    val logs: VictoriaLogsClient,
    val traces: VictoriaTracesClient,
    val kubernetes: KubernetesClient,
)
