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
package com.opssage.sre.logs

import com.opssage.sre.client.VictoriaLogsClient
import com.opssage.sre.config.LogsProperties
import com.opssage.sre.dto.TimeWindowView
import com.opssage.sre.dto.TopError
import com.opssage.sre.dto.TopLogErrorsResult
import com.opssage.sre.model.LogRecord
import com.opssage.sre.time.TimeWindow
import com.opssage.sre.util.ConfidenceCalculator
import reactor.core.publisher.Mono

import org.springframework.stereotype.Component

@Component
class LogsService(
    private val client: VictoriaLogsClient,
    private val aggregator: LogErrorAggregator,
    private val logs: LogsProperties,
) {

    fun topLogErrors(
        request: LogQuery,
        window: TimeWindow,
    ): Mono<TopLogErrorsResult> =
        client
            .errorLogs(LogSearch(request.service, request.namespace, window))
            .map { records ->
                val top = aggregator.aggregate(records, request.limit)
                TopLogErrorsResult(
                    service = request.service,
                    namespace = request.namespace,
                    window = view(window),
                    topErrors = top,
                    summary = summaryLine(request.service, top, records.size),
                    confidence =
                        ConfidenceCalculator.ofSamples(
                            records.size,
                            logs.maxScanSamples,
                        ),
                )
            }

    fun matchingLogErrors(
        request: LogQuery,
        text: String,
        window: TimeWindow,
    ): Mono<TopLogErrorsResult> =
        client
            .errorLogs(LogSearch(request.service, request.namespace, window))
            .map { records ->
                val matched = records.filter { matches(it, text) }
                val top = aggregator.aggregate(matched, request.limit)
                TopLogErrorsResult(
                    service = request.service,
                    namespace = request.namespace,
                    window = view(window),
                    topErrors = top,
                    summary =
                        matchingSummary(
                            request.service,
                            text,
                            top,
                            records.size,
                        ),
                    confidence = scanConfidence(matched, records),
                )
            }

    private fun summaryLine(
        service: String,
        top: List<TopError>,
        total: Int,
    ): String =
        "Top log errors for $service: $total error lines grouped into " +
            "${top.size} fingerprints."

    private fun matchingSummary(
        service: String,
        text: String,
        top: List<TopError>,
        scanned: Int,
    ): String =
        "Matching log errors for $service containing '$text': " +
            "${top.sumOf { it.count }} matching error lines grouped into " +
            "${top.size} fingerprints from $scanned scanned error lines."

    private fun scanConfidence(
        matched: List<LogRecord>,
        scanned: List<LogRecord>,
    ) = if (matched.isEmpty()) {
        ConfidenceCalculator.ofSamples(0, 1)
    } else {
        ConfidenceCalculator.ofSamples(scanned.size, logs.maxScanSamples)
    }

    private fun matches(
        record: LogRecord,
        text: String,
    ): Boolean {
        val needle = text.lowercase()
        return record.message.lowercase().contains(needle) ||
            record.traceId
                .orEmpty()
                .lowercase()
                .contains(needle)
    }

    private fun view(window: TimeWindow): TimeWindowView =
        TimeWindowView(window.from.toString(), window.to.toString())
}
