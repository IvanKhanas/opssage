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

import com.opssage.sre.dto.ContractCheck
import com.opssage.sre.dto.ContractStatus
import com.opssage.sre.dto.TelemetryContractResult
import com.opssage.sre.time.TimeWindow
import reactor.core.publisher.Mono

import org.springframework.stereotype.Component

@Component
class TelemetryContractQuery(
    private val metrics: MetricsContractProbe,
    private val signals: SignalContractProbe,
) {

    fun run(
        namespace: String,
        window: TimeWindow,
    ): Mono<TelemetryContractResult> =
        Mono
            .zip(
                metrics.checks(namespace, window),
                signals.checks(namespace, window),
            ).map { both -> assemble(namespace, both.t1 + both.t2) }

    private fun assemble(
        namespace: String,
        checks: List<ContractCheck>,
    ): TelemetryContractResult =
        TelemetryContractResult(
            namespace = namespace,
            checks = checks,
            summary = summarize(checks),
        )

    private fun summarize(checks: List<ContractCheck>): String {
        val broken = checks.count { it.status == ContractStatus.MISCONFIGURED }
        val absent = checks.count { it.status == ContractStatus.ABSENT }
        val unknown = checks.count { it.status == ContractStatus.UNKNOWN }
        if (broken == 0 && absent == 0 && unknown == 0) {
            return "All ${checks.size} telemetry contract checks passed."
        }
        return "$broken misconfigured, $absent absent, " +
            "$unknown unverifiable of ${checks.size} telemetry contract " +
            "checks. MISCONFIGURED means " +
            "OpsSage reads the wrong thing and may call a broken service " +
            "healthy. ABSENT means the capability stays silent. UNKNOWN " +
            "means the schema is right but the window holds no data to " +
            "confirm it."
    }
}
