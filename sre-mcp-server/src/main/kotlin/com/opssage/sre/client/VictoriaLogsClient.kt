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

import com.opssage.sre.config.LogsProperties
import com.opssage.sre.model.LogRecord
import com.opssage.sre.time.TimeWindow
import io.github.oshai.kotlinlogging.KotlinLogging
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper

import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

private val log = KotlinLogging.logger {}

@Component
class VictoriaLogsClient(
    private val victoriaLogsWebClient: WebClient,
    private val logs: LogsProperties,
    private val mapper: ObjectMapper,
) {

    fun errorLogs(
        service: String,
        namespace: String,
        window: TimeWindow,
    ): Mono<List<LogRecord>> =
        Flux
            .range(0, pageCount())
            .concatMap { page ->
                errorLogPage(
                    service,
                    namespace,
                    window,
                    page * logs.maxSamples,
                )
            }.takeUntil { it.size < logs.maxSamples }
            .flatMapIterable { it }
            .take(logs.maxScanSamples.toLong())
            .collectList()

    private fun errorLogPage(
        service: String,
        namespace: String,
        window: TimeWindow,
        offset: Int,
    ): Mono<List<LogRecord>> =
        victoriaLogsWebClient
            .get()
            .uri { builder ->
                builder
                    .path("/select/logsql/query")
                    .queryParam("query", "{query}")
                    .build(
                        mapOf(
                            "query" to
                                query(service, namespace, window, offset),
                        ),
                    )
            }.retrieve()
            .bodyToMono(String::class.java)
            .defaultIfEmpty("")
            .map(::parse)
            .doOnError { error ->
                log.atWarn {
                    message = "VictoriaLogs query failed"
                    payload =
                        mapOf("service" to service, "namespace" to namespace)
                    cause = error
                }
            }

    private fun query(
        service: String,
        namespace: String,
        window: TimeWindow,
        offset: Int,
    ): String =
        "${logs.serviceField}:=${quote(service)} " +
            "${logs.namespaceField}:=${quote(namespace)} " +
            "${logs.levelField}:=${quote(logs.errorLevel)} " +
            "${logs.timeField}:[${window.from}, ${window.to}] " +
            "| sort by (${logs.timeField} desc) | offset $offset " +
            "| limit ${logs.maxSamples}"

    private fun pageCount(): Int =
        (logs.maxScanSamples + logs.maxSamples - 1) / logs.maxSamples

    private fun quote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun parse(body: String): List<LogRecord> =
        body
            .lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull(::parseLine)
            .toList()

    private fun parseLine(line: String): LogRecord? =
        try {
            val node = mapper.readValue(line, Map::class.java)
            LogRecord(
                message = node[logs.messageField]?.toString().orEmpty(),
                traceId = node[logs.traceField]?.toString(),
                timestamp = node[logs.timeField]?.toString().orEmpty(),
            )
        } catch (error: RuntimeException) {
            log.atWarn {
                message = "Skipping malformed log line"
                cause = error
            }
            null
        }
}
