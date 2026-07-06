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

data class RolloutMetrics(
    val errorRate: Double,
    val p95Ms: Double,
    val p99Ms: Double,
)

data class RolloutDelta(
    val errorRateChange: String,
    val p95Change: String,
    val p99Change: String,
)

data class NewError(
    val fingerprint: String,
    val count: Long,
)

data class RolloutComparisonResult(
    val service: String,
    val deployTime: String,
    val before: RolloutMetrics,
    val after: RolloutMetrics,
    val delta: RolloutDelta,
    val newErrors: List<NewError>,
    val narrative: String,
    val confidence: Confidence,
)
