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

import com.opssage.sre.client.VictoriaMetricsClient
import com.opssage.sre.time.TimeWindow
import reactor.core.publisher.Mono

import org.springframework.stereotype.Component

@Component
class MetricReadings(
    private val client: VictoriaMetricsClient,
    private val summarizer: MetricSummarizer,
) {

    fun reading(
        window: TimeWindow,
        promql: String,
        step: Long,
    ): Mono<Reading> =
        client.queryRange(promql, window, step).map { series ->
            val stats = summarizer.summarizeFirst("value", series).stats
            if (stats == null) {
                Reading(0.0, false)
            } else {
                Reading(stats.mean, true)
            }
        }
}
