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

import com.opssage.sre.dto.SpanSummary
import com.opssage.sre.dto.TraceDetailResult
import com.opssage.sre.dto.TraceSummary
import com.opssage.sre.model.Span
import com.opssage.sre.model.Trace
import com.opssage.sre.util.ConfidenceCalculator
import com.opssage.sre.util.Numbers

import java.time.Instant

import org.springframework.stereotype.Component

@Component
class TraceAssembler {

    fun summarize(trace: Trace): TraceSummary {
        val spans = trace.spans
        val root = root(spans)
        return TraceSummary(
            traceId = trace.traceId,
            rootService = root?.serviceName.orEmpty(),
            rootOperation = root?.operationName.orEmpty(),
            startTime = startTime(spans),
            durationMs = Numbers.microsToMillis(totalMicros(spans)),
            spanCount = spans.size,
            serviceCount = serviceCount(spans),
            errorSpanCount = spans.count { it.error },
        )
    }

    fun detail(
        trace: Trace,
        maxSpans: Int,
    ): TraceDetailResult {
        val spans = trace.spans
        val root = root(spans)
        val depths = depths(spans)
        val slowest = spans.maxByOrNull { it.durationMicros }
        return TraceDetailResult(
            traceId = trace.traceId,
            rootService = root?.serviceName.orEmpty(),
            rootOperation = root?.operationName.orEmpty(),
            startTime = startTime(spans),
            totalDurationMs = Numbers.microsToMillis(totalMicros(spans)),
            spanCount = spans.size,
            serviceCount = serviceCount(spans),
            errorSpanCount = spans.count { it.error },
            slowestSpan = slowest?.let(::describe).orEmpty(),
            spans =
                spans
                    .sortedBy { it.startTimeMicros }
                    .take(maxOf(maxSpans, 1))
                    .map { span ->
                        SpanSummary(
                            depth = depths[span.spanId] ?: 0,
                            service = span.serviceName,
                            operation = span.operationName,
                            durationMs =
                                Numbers.microsToMillis(span.durationMicros),
                            error = span.error,
                        )
                    },
            summary = detailSummary(trace.traceId, spans),
            confidence =
                ConfidenceCalculator.of(if (spans.isEmpty()) 0 else 1, 1),
        )
    }

    private fun root(spans: List<Span>): Span? {
        val ids = spans.mapTo(HashSet()) { it.spanId }
        return spans
            .filter { it.parentSpanId == null || it.parentSpanId !in ids }
            .minByOrNull { it.startTimeMicros }
            ?: spans.minByOrNull { it.startTimeMicros }
    }

    private fun depths(spans: List<Span>): Map<String, Int> {
        val byId = spans.associateBy { it.spanId }
        val cache = HashMap<String, Int>()

        fun depthOf(
            span: Span,
            seen: Set<String>,
        ): Int {
            cache[span.spanId]?.let { return it }
            val parent = span.parentSpanId?.let { byId[it] }
            val depth =
                if (parent == null || parent.spanId in seen) {
                    0
                } else {
                    1 + depthOf(parent, seen + span.spanId)
                }
            cache[span.spanId] = depth
            return depth
        }

        spans.forEach { depthOf(it, emptySet()) }
        return cache
    }

    private fun totalMicros(spans: List<Span>): Long {
        if (spans.isEmpty()) return 0
        val start = spans.minOf { it.startTimeMicros }
        val end = spans.maxOf { it.startTimeMicros + it.durationMicros }
        return end - start
    }

    private fun startTime(spans: List<Span>): String {
        if (spans.isEmpty()) return ""
        val micros = spans.minOf { it.startTimeMicros }
        return Instant.ofEpochMilli(micros / MICROS_PER_MILLI).toString()
    }

    private fun serviceCount(spans: List<Span>): Int =
        spans
            .map { it.serviceName }
            .filter { it.isNotBlank() }
            .distinct()
            .size

    private fun describe(span: Span): String =
        "${span.serviceName} ${span.operationName} " +
            "(${Numbers.microsToMillis(span.durationMicros)}ms)"

    private fun detailSummary(
        traceId: String,
        spans: List<Span>,
    ): String {
        if (spans.isEmpty()) return "Trace $traceId: no spans found."
        val errors = spans.count { it.error }
        return "Trace $traceId: ${spans.size} spans across " +
            "${serviceCount(spans)} services, $errors with errors."
    }

    private companion object {
        const val MICROS_PER_MILLI = 1000L
    }
}
