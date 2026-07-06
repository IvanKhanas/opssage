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
package com.opssage.sre.metrics

import com.opssage.sre.client.VictoriaLogsClient
import com.opssage.sre.dto.NewError
import com.opssage.sre.logs.LogErrorAggregator
import com.opssage.sre.time.TimeWindow
import reactor.core.publisher.Mono

import org.springframework.stereotype.Component

@Component
class RolloutErrorDiff(
    private val logsClient: VictoriaLogsClient,
    private val aggregator: LogErrorAggregator,
) {

    fun newErrors(
        service: String,
        namespace: String,
        before: TimeWindow,
        after: TimeWindow,
        limit: Int,
    ): Mono<List<NewError>> =
        Mono.zip(
            logsClient.errorLogs(service, namespace, before),
            logsClient.errorLogs(service, namespace, after),
        ) { beforeLogs, afterLogs ->
            val known =
                beforeLogs
                    .map { aggregator.fingerprint(it.message) }
                    .toSet()
            afterLogs
                .map { aggregator.fingerprint(it.message) }
                .filter { it !in known }
                .groupingBy { it }
                .eachCount()
                .map { (fingerprint, count) ->
                    NewError(fingerprint, count.toLong())
                }.sortedByDescending { it.count }
                .take(maxOf(limit, 1))
        }
}
