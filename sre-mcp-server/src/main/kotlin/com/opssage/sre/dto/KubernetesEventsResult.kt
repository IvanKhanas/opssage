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

import com.fasterxml.jackson.annotation.JsonInclude
import com.opssage.sre.model.Confidence

data class PodStatusView(
    val name: String,
    val phase: String,
    val ready: Boolean,
    val restartCount: Int,
    @get:JsonInclude(JsonInclude.Include.NON_EMPTY)
    val reason: String,
)

data class ClusterEventView(
    val type: String,
    val reason: String,
    val message: String,
    val involvedObject: String,
    val count: Int,
    val lastSeen: String,
)

data class KubernetesEventsResult(
    val service: String,
    val namespace: String,
    val pods: List<PodStatusView>,
    val events: List<ClusterEventView>,
    val warningCount: Int,
    val notReadyPodCount: Int,
    val summary: String,
    val confidence: Confidence,
)
