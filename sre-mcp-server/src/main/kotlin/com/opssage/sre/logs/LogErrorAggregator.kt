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

import com.opssage.sre.dto.TopError
import com.opssage.sre.model.LogRecord

import org.springframework.stereotype.Component

@Component
class LogErrorAggregator {

    fun aggregate(
        records: List<LogRecord>,
        limit: Int,
    ): List<TopError> =
        records
            .groupBy { fingerprint(it.message) }
            .map { (fingerprint, group) -> toTopError(fingerprint, group) }
            .sortedByDescending { it.count }
            .take(maxOf(limit, 1))

    fun fingerprint(message: String): String =
        firstLine(message)
            .replace(UUID, "<uuid>")
            .replace(URL, "<url>")
            .replace(IP, "<ip>")
            .replace(HEX, "<hex>")
            .replace(QUOTED, "<str>")
            .replace(NUMBER, "<num>")
            .replace(WHITESPACE, " ")
            .trim()

    private fun toTopError(
        fingerprint: String,
        group: List<LogRecord>,
    ): TopError {
        val times =
            group
                .map { it.timestamp }
                .filter { it.isNotBlank() }
                .sorted()
        val traces =
            group
                .mapNotNull { it.traceId }
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_SAMPLE_TRACES)
        return TopError(
            fingerprint = fingerprint,
            count = group.size.toLong(),
            firstSeen = times.firstOrNull().orEmpty(),
            lastSeen = times.lastOrNull().orEmpty(),
            sampleTraceIds = traces,
            sampleMessage = firstLine(group.first().message),
        )
    }

    private fun firstLine(message: String): String =
        message
            .lineSequence()
            .firstOrNull()
            .orEmpty()
            .trim()

    private companion object {
        const val MAX_SAMPLE_TRACES = 5
        val UUID =
            Regex(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-" +
                    "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
            )
        val URL = Regex("https?://\\S+")
        val IP = Regex("\\b\\d{1,3}(\\.\\d{1,3}){3}\\b")
        val HEX = Regex("\\b0x[0-9a-fA-F]+\\b")
        val QUOTED = Regex("\"[^\"]*\"")
        val NUMBER = Regex("\\d+")
        val WHITESPACE = Regex("\\s+")
    }
}
