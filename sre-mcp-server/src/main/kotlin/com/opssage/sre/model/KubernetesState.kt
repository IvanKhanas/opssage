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

data class PodStatus(
    val name: String,
    val phase: String,
    val ready: Boolean,
    val restartCount: Int,
    val reason: String,
)

data class ClusterEvent(
    val type: String,
    val reason: String,
    val message: String,
    val objectKind: String,
    val objectName: String,
    val count: Int,
    val lastSeen: String,
)

data class ServiceKubernetesState(
    val pods: List<PodStatus>,
    val events: List<ClusterEvent>,
)
