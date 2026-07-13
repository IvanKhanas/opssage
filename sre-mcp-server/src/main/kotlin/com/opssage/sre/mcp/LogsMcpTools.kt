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

import com.opssage.sre.config.LogsProperties
import com.opssage.sre.config.McpProperties
import com.opssage.sre.dto.TopLogErrorsResult
import com.opssage.sre.logs.LogQuery
import com.opssage.sre.logs.LogsService
import com.opssage.sre.time.TimeWindowResolver
import com.opssage.sre.util.Identifiers
import com.opssage.sre.util.LimitBounds
import com.opssage.sre.util.blockingGet

import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component

@Component
class LogsMcpTools(
    private val logsService: LogsService,
    private val resolver: TimeWindowResolver,
    private val logs: LogsProperties,
    private val mcp: McpProperties,
) : McpToolSet {

    @Tool(
        description =
            "Find and group the top error-level log entries for a service " +
                "over a recent window. Errors are grouped into fingerprints " +
                "with a count, first/last seen time and sample trace ids. " +
                "Provide 'lookback' as an ISO-8601 duration such as PT1H. " +
                "'limit' is the optional max number of fingerprints to " +
                "return: omit it to use the server default, capped " +
                "server-side.",
    )
    fun findTopLogErrors(
        service: String,
        namespace: String,
        lookback: String?,
        limit: Int?,
    ): TopLogErrorsResult {
        Identifiers.require("service", service)
        Identifiers.require("namespace", namespace)
        val window = resolver.fromLookback(lookback)
        val request =
            LogQuery(
                service,
                namespace,
                LimitBounds.bound(limit, logs.maxSamples),
            )
        return logsService
            .topLogErrors(request, window)
            .blockingGet(mcp.callTimeout, "finding log errors for $service")
    }

    @Tool(
        description =
            "Find error-level log entries for a service that contain a " +
                "specific literal text in the log message or trace id. Use " +
                "this for user complaints or incident hints such as an id, " +
                "email, request id, trace id, order id, or exact error " +
                "token. " +
                "The search is bounded by service, namespace, lookback and " +
                "server-side scan limits; it does not accept arbitrary " +
                "LogsQL. 'limit' is the optional max number of fingerprints, " +
                "capped server-side. Confidence reflects how completely the " +
                "bounded error window was scanned, not how strongly the text " +
                "matched.",
    )
    fun findLogErrorsByText(
        service: String,
        namespace: String,
        query: String,
        lookback: String?,
        limit: Int?,
    ): TopLogErrorsResult {
        Identifiers.require("service", service)
        Identifiers.require("namespace", namespace)
        Identifiers.requireValue("query", query)
        val window = resolver.fromLookback(lookback)
        val request =
            LogQuery(
                service,
                namespace,
                LimitBounds.bound(limit, logs.maxSamples),
            )
        return logsService
            .matchingLogErrors(request, query, window)
            .blockingGet(
                mcp.callTimeout,
                "finding log errors for $service containing query",
            )
    }
}
