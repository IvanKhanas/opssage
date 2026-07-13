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

data class JaegerResponse(
    val data: List<JaegerTrace> = emptyList(),
)

data class JaegerTrace(
    val traceID: String = "",
    val spans: List<JaegerSpan> = emptyList(),
    val processes: Map<String, JaegerProcess> = emptyMap(),
)

data class JaegerSpan(
    val spanID: String = "",
    val operationName: String = "",
    val references: List<JaegerReference> = emptyList(),
    val startTime: Long = 0,
    val duration: Long = 0,
    val tags: List<JaegerTag> = emptyList(),
    val processID: String = "",
)

data class JaegerReference(
    val refType: String = "",
    val spanID: String = "",
)

data class JaegerTag(
    val key: String = "",
    val value: Any? = null,
)

data class JaegerProcess(
    val serviceName: String = "",
    val tags: List<JaegerTag> = emptyList(),
)

data class JaegerServicesResponse(
    val data: List<String> = emptyList(),
)
