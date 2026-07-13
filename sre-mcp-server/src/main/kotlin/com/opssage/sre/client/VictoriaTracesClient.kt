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
package com.opssage.sre.client

import com.opssage.sre.config.TracesProperties
import com.opssage.sre.model.Span
import com.opssage.sre.model.Trace
import com.opssage.sre.time.TimeWindow
import io.github.oshai.kotlinlogging.KotlinLogging
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper

import java.time.Instant

import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

private val log = KotlinLogging.logger {}

@Component
class VictoriaTracesClient(
    private val victoriaTracesWebClient: WebClient,
    private val traces: TracesProperties,
    private val mapper: ObjectMapper,
) {

    fun services(): Mono<List<String>> =
        victoriaTracesWebClient
            .get()
            .uri { builder ->
                builder.path("${traces.jaegerApiPath}/services").build()
            }.retrieve()
            .bodyToMono<JaegerServicesResponse>()
            .map(JaegerServicesResponse::data)

    fun findTraces(
        service: String,
        namespace: String,
        userId: String,
        window: TimeWindow,
        limit: Int,
    ): Mono<List<Trace>> =
        victoriaTracesWebClient
            .get()
            .uri { builder ->
                builder
                    .path("${traces.jaegerApiPath}/traces")
                    .queryParam("service", service)
                    .queryParam("start", micros(window.from))
                    .queryParam("end", micros(window.to))
                    .queryParam("limit", candidateLimit(limit))
                if (userId.isBlank()) {
                    return@uri builder.build()
                }
                builder
                    .queryParam("tags", TAGS_TEMPLATE)
                    .build(mapOf(TAGS_VARIABLE to userTag(userId)))
            }.retrieve()
            .bodyToMono<JaegerResponse>()
            .map { response ->
                response.data
                    .filter { belongsTo(it, namespace) }
                    .take(limit)
                    .map(::toTrace)
            }.doOnError { error ->
                log.atWarn {
                    message = "VictoriaTraces search failed"
                    payload =
                        mapOf("service" to service, "namespace" to namespace)
                    cause = error
                }
            }

    fun findServiceTraces(
        service: String,
        namespace: String,
        window: TimeWindow,
        limit: Int,
    ): Mono<List<Trace>> = findTraces(service, namespace, "", window, limit)

    fun getTrace(traceId: String): Mono<Trace> =
        victoriaTracesWebClient
            .get()
            .uri { builder ->
                builder
                    .path(
                        "${traces.jaegerApiPath}/traces/{id}",
                    ).build(traceId)
            }.retrieve()
            .bodyToMono<JaegerResponse>()
            .map { response ->
                response.data.firstOrNull()?.let(::toTrace)
                    ?: Trace(traceId, emptyList())
            }.doOnError { error ->
                log.atWarn {
                    message = "VictoriaTraces lookup failed"
                    payload = mapOf("traceId" to traceId)
                    cause = error
                }
            }

    private fun candidateLimit(limit: Int): Int {
        val candidates = limit.toLong() * traces.searchCandidateMultiplier
        return candidates.coerceAtMost(MAX_CANDIDATE_TRACES).toInt()
    }

    private fun userTag(userId: String): String =
        mapper.writeValueAsString(mapOf(traces.userTag to userId))

    private fun belongsTo(
        trace: JaegerTrace,
        namespace: String,
    ): Boolean =
        hasNamespace(trace.processes.values.flatMap { it.tags }, namespace) ||
            hasNamespace(trace.spans.flatMap { it.tags }, namespace)

    private fun hasNamespace(
        tags: List<JaegerTag>,
        namespace: String,
    ): Boolean =
        tags.any { tag ->
            tag.key == traces.namespaceTag &&
                tag.value?.toString() == namespace
        }

    private fun micros(instant: Instant): Long =
        instant.toEpochMilli() * MICROS_PER_MILLI

    private fun toTrace(trace: JaegerTrace): Trace {
        val services = trace.processes.mapValues { it.value.serviceName }
        return Trace(trace.traceID, trace.spans.map { toSpan(it, services) })
    }

    private fun toSpan(
        span: JaegerSpan,
        services: Map<String, String>,
    ): Span =
        Span(
            spanId = span.spanID,
            parentSpanId =
                span.references
                    .firstOrNull { it.refType == CHILD_OF }
                    ?.spanID
                    ?.takeIf { it.isNotBlank() },
            serviceName = services[span.processID].orEmpty(),
            operationName = span.operationName,
            startTimeMicros = span.startTime,
            durationMicros = span.duration,
            error = hasError(span.tags),
        )

    private fun hasError(tags: List<JaegerTag>): Boolean =
        tags.any { it.key == traces.errorTag && truthy(it.value) }

    private fun truthy(value: Any?): Boolean {
        val text = value?.toString()?.trim().orEmpty()
        return text.isNotEmpty() && text.lowercase() !in NON_ERROR_VALUES
    }

    private companion object {
        const val CHILD_OF = "CHILD_OF"
        const val MICROS_PER_MILLI = 1000L
        const val MAX_CANDIDATE_TRACES = 50_000L
        const val TAGS_VARIABLE = "tagsFilter"
        const val TAGS_TEMPLATE = "{$TAGS_VARIABLE}"
        val NON_ERROR_VALUES = setOf("false", "0", "unset", "ok")
    }
}
