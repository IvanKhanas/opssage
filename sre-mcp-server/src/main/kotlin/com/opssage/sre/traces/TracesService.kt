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
package com.opssage.sre.traces

import com.opssage.sre.client.VictoriaTracesClient
import com.opssage.sre.config.QueryProperties
import com.opssage.sre.dto.TimeWindowView
import com.opssage.sre.dto.TraceDetailResult
import com.opssage.sre.dto.UserTracesResult
import com.opssage.sre.time.TimeWindow
import com.opssage.sre.util.ConfidenceCalculator
import reactor.core.publisher.Mono

import org.springframework.stereotype.Component

@Component
class TracesService(
    private val client: VictoriaTracesClient,
    private val assembler: TraceAssembler,
    private val query: QueryProperties,
) {

    fun userTraces(
        request: TraceQuery,
        window: TimeWindow,
    ): Mono<UserTracesResult> {
        val limit = request.limit.coerceIn(1, query.maxTraces)
        return client
            .findTraces(
                request.service,
                request.namespace,
                request.userId,
                window,
                limit,
            ).map { traces ->
                UserTracesResult(
                    service = request.service,
                    namespace = request.namespace,
                    userId = request.userId,
                    window = TimeWindowView.of(window),
                    traces = traces.map(assembler::summarize),
                    summary = summaryLine(request.userId, traces.size),
                    confidence =
                        ConfidenceCalculator.ofSamples(traces.size, limit),
                )
            }
    }

    fun traceDetail(traceId: String): Mono<TraceDetailResult> =
        client
            .getTrace(traceId)
            .map { trace -> assembler.detail(trace, query.maxSpans) }

    private fun summaryLine(
        userId: String,
        found: Int,
    ): String = "Found $found traces related to user $userId."
}
