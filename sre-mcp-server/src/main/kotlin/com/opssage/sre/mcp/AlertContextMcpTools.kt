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

import com.opssage.sre.alert.AlertContextQuery
import com.opssage.sre.config.McpProperties
import com.opssage.sre.dto.AlertContextResult
import com.opssage.sre.time.TimeWindowResolver
import com.opssage.sre.util.Identifiers
import com.opssage.sre.util.blockingGet

import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component

@Component
class AlertContextMcpTools(
    private val alertContextQuery: AlertContextQuery,
    private val resolver: TimeWindowResolver,
    private val mcp: McpProperties,
) : McpToolSet {

    @Tool(
        description =
            "Gather a one-shot context snapshot for an alert on a service: " +
                "its metric health signals (request/error rate, latency), " +
                "the top log error fingerprints and the current Kubernetes " +
                "pod status and warning events, all over one recent window. " +
                "Provide 'lookback' as an ISO-8601 duration such as PT30M. " +
                "Use it as the first triage call for an alert; the caller " +
                "judges the likely cause and may drill in with the " +
                "per-source tools.",
    )
    fun getAlertContext(
        service: String,
        namespace: String,
        lookback: String?,
    ): AlertContextResult {
        Identifiers.require("service", service)
        Identifiers.require("namespace", namespace)
        val window = resolver.fromLookback(lookback)
        return alertContextQuery
            .run(service, namespace, window)
            .blockingGet(
                mcp.callTimeout,
                "gathering alert context for $service",
            )
    }
}
