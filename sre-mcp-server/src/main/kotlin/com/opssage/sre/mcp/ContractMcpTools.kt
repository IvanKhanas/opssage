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
package com.opssage.sre.mcp

import com.opssage.sre.config.McpProperties
import com.opssage.sre.contract.TelemetryContractQuery
import com.opssage.sre.dto.TelemetryContractResult
import com.opssage.sre.time.TimeWindowResolver
import com.opssage.sre.util.Identifiers
import com.opssage.sre.util.blockingGet

import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component

@Component
class ContractMcpTools(
    private val contract: TelemetryContractQuery,
    private val resolver: TimeWindowResolver,
    private val mcp: McpProperties,
) : McpToolSet {

    @Tool(
        description =
            "Check whether this cluster actually provides the telemetry " +
                "OpsSage expects: the request and histogram metrics, the " +
                "service and namespace labels, the error matcher, the " +
                "correctness metric, the log levels, the trace backend and " +
                "the Kubernetes API. Use it when a tool returns nothing and " +
                "you cannot tell whether the service is healthy or the " +
                "telemetry is wired wrong. MISCONFIGURED means OpsSage reads " +
                "the wrong thing; ABSENT means a capability is simply not " +
                "installed; UNKNOWN means the window holds no data to judge.",
    )
    fun validateTelemetryContract(
        namespace: String,
        lookback: String?,
    ): TelemetryContractResult {
        Identifiers.require("namespace", namespace)
        val window = resolver.fromLookback(lookback)
        return contract
            .run(namespace, window)
            .blockingGet(
                mcp.callTimeout,
                "validating the telemetry contract for $namespace",
            )
    }
}
