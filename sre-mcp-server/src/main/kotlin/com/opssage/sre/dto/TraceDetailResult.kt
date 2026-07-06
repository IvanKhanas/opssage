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

data class SpanSummary(
    val depth: Int,
    val service: String,
    val operation: String,
    val durationMs: Double,
    val error: Boolean,
)

data class TraceDetailResult(
    val traceId: String,
    val rootService: String,
    val rootOperation: String,
    val startTime: String,
    val totalDurationMs: Double,
    val spanCount: Int,
    val serviceCount: Int,
    val errorSpanCount: Int,
    val slowestSpan: String,
    val spans: List<SpanSummary>,
    val summary: String,
    val confidence: Confidence,
)
