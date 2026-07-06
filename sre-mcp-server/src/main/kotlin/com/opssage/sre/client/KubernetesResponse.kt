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

data class K8sPodList(
    val items: List<K8sPod> = emptyList(),
)

data class K8sPod(
    val metadata: K8sMetadata = K8sMetadata(),
    val status: K8sPodStatus = K8sPodStatus(),
)

data class K8sMetadata(
    val name: String = "",
)

data class K8sPodStatus(
    val phase: String = "",
    val reason: String = "",
    val containerStatuses: List<K8sContainerStatus> = emptyList(),
)

data class K8sContainerStatus(
    val ready: Boolean = false,
    val restartCount: Int = 0,
    val state: K8sContainerState = K8sContainerState(),
)

data class K8sContainerState(
    val waiting: K8sStateDetail? = null,
)

data class K8sStateDetail(
    val reason: String = "",
)

data class K8sEventList(
    val items: List<K8sEvent> = emptyList(),
)

data class K8sEvent(
    val type: String = "",
    val reason: String = "",
    val message: String = "",
    val count: Int = 0,
    val lastTimestamp: String? = null,
    val eventTime: String? = null,
    val involvedObject: K8sInvolvedObject = K8sInvolvedObject(),
)

data class K8sInvolvedObject(
    val kind: String = "",
    val name: String = "",
)
