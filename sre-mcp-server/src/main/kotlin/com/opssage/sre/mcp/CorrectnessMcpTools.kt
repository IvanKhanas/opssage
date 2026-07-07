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
import com.opssage.sre.dto.ServiceCorrectnessResult
import com.opssage.sre.metrics.ServiceCorrectnessQuery
import com.opssage.sre.time.TimeWindowResolver
import com.opssage.sre.util.Identifiers
import com.opssage.sre.util.blockingGet

import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component

@Component
class CorrectnessMcpTools(
    private val correctness: ServiceCorrectnessQuery,
    private val resolver: TimeWindowResolver,
    private val mcp: McpProperties,
) {

    @Tool(
        description =
            "Return failure ratios for instrumented business correctness " +
                "invariants of a service. Use it when HTTP requests succeed " +
                "but responses may be semantically wrong, incomplete or " +
                "inconsistent. Invariant names are the values of the " +
                "invariant label the service emits on its correctness " +
                "metric; declare the expected ones on the service profile. " +
                "LOW confidence means no correctness telemetry was observed.",
    )
    fun getServiceCorrectness(
        service: String,
        namespace: String,
        lookback: String?,
    ): ServiceCorrectnessResult {
        Identifiers.require("service", service)
        Identifiers.require("namespace", namespace)
        val window = resolver.fromLookback(lookback)
        return correctness
            .run(service, namespace, window)
            .blockingGet(
                mcp.callTimeout,
                "reading correctness signals for $service",
            )
    }
}
