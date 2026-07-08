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
package com.opssage.agent.model

import java.time.Instant

data class ToolCall(
    val server: String,
    val name: String,
    val arguments: String,
)

data class ToolResult(
    val summary: String,
    val confidence: Confidence,
    val evidence: List<String> = emptyList(),
)

data class Message(
    val id: String,
    val role: MessageRole,
    val content: String,
    val toolCall: ToolCall? = null,
    val toolResult: ToolResult? = null,
    val masked: Boolean = false,
    val createdAt: Instant = Instant.now(),
)
