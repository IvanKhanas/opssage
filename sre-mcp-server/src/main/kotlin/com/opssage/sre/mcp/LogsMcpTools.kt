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
import com.opssage.sre.util.blockingGet

import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component

@Component
class LogsMcpTools(
    private val logsService: LogsService,
    private val resolver: TimeWindowResolver,
    private val logs: LogsProperties,
    private val mcp: McpProperties,
) {

    @Tool(
        description =
            "Find and group the top error-level log entries for a service " +
                "over a recent window. Errors are grouped into fingerprints " +
                "with a count, first/last seen time and sample trace ids. " +
                "Provide 'lookback' as an ISO-8601 duration such as PT1H and " +
                "'limit' as the number of fingerprints to return.",
    )
    fun findTopLogErrors(
        service: String,
        namespace: String,
        lookback: String?,
        limit: Int,
    ): TopLogErrorsResult {
        Identifiers.require("service", service)
        Identifiers.require("namespace", namespace)
        val window = resolver.fromLookback(lookback)
        val request =
            LogQuery(service, namespace, limit.coerceIn(1, logs.maxSamples))
        return logsService
            .topLogErrors(request, window)
            .blockingGet(mcp.callTimeout, "finding log errors for $service")
    }
}
