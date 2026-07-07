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
package com.opssage.sre.dto

import com.opssage.sre.model.Confidence
import com.opssage.sre.model.MetricSummary
import com.opssage.sre.time.TimeWindow

data class TimeWindowView(
    val from: String,
    val to: String,
) {
    companion object {
        fun of(window: TimeWindow): TimeWindowView =
            TimeWindowView(window.from.toString(), window.to.toString())
    }
}

data class ServiceHealthResult(
    val service: String,
    val namespace: String,
    val window: TimeWindowView,
    val signals: List<MetricSummary>,
    val summary: String,
    val confidence: Confidence,
)
