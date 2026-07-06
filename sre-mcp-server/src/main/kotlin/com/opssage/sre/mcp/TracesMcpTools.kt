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
import com.opssage.sre.dto.TraceDetailResult
import com.opssage.sre.dto.UserTracesResult
import com.opssage.sre.time.TimeWindowResolver
import com.opssage.sre.traces.TraceQuery
import com.opssage.sre.traces.TracesService
import com.opssage.sre.util.Identifiers
import com.opssage.sre.util.blockingGet

import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component

@Component
class TracesMcpTools(
    private val tracesService: TracesService,
    private val resolver: TimeWindowResolver,
    private val mcp: McpProperties,
) {

    @Tool(
        description =
            "Find distributed traces tied to a specific end user for a " +
                "service over a recent window, to see which requests that " +
                "user made and which failed. Provide 'userId' as the user " +
                "identifier, 'lookback' as an ISO-8601 duration such as PT1H " +
                "and 'limit' as the max number of traces. Each trace is " +
                "summarised with root operation, duration, span/service " +
                "counts and error-span count; the caller picks which to " +
                "inspect with summarizeTrace.",
    )
    fun findUserRelatedTraces(
        service: String,
        namespace: String,
        userId: String,
        lookback: String?,
        limit: Int,
    ): UserTracesResult {
        Identifiers.require("service", service)
        Identifiers.require("namespace", namespace)
        Identifiers.requireValue("userId", userId)
        val window = resolver.fromLookback(lookback)
        return tracesService
            .userTraces(TraceQuery(service, namespace, userId, limit), window)
            .blockingGet(mcp.callTimeout, "finding traces for user $userId")
    }

    @Tool(
        description =
            "Summarise a single distributed trace by its id into an ordered " +
                "span chain: for each span the service, operation, call " +
                "depth, duration and error flag, plus total duration, " +
                "span/service counts, error-span count and the slowest span. " +
                "Use it to localise where in a request the latency or error " +
                "occurred; the caller judges the root cause.",
    )
    fun summarizeTrace(traceId: String): TraceDetailResult {
        Identifiers.require("traceId", traceId)
        return tracesService
            .traceDetail(traceId)
            .blockingGet(mcp.callTimeout, "summarizing trace $traceId")
    }
}
