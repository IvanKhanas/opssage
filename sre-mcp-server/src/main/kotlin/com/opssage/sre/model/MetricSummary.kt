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
package com.opssage.sre.model

import com.fasterxml.jackson.annotation.JsonInclude

data class MetricSummary(
    val metricName: String,
    @get:JsonInclude(JsonInclude.Include.NON_EMPTY)
    val labels: Map<String, String>,
    val datapointCount: Int,
    val stats: MetricStats?,
)

data class MetricStats(
    val first: Double,
    val latest: Double,
    val min: Double,
    val max: Double,
    val mean: Double,
    val p95: Double,
    val trend: String,
    val delta: Double,
    val windowMinutes: Double,
)
