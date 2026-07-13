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
package com.opssage.agent.masking

import io.github.oshai.kotlinlogging.KotlinLogging

import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class MaskedToolRegistry(
    toolCallbackProviders: List<ToolCallbackProvider>,
    piiMasker: PiiMasker,
) {

    val callbacks: List<ToolCallback> =
        toolCallbackProviders
            .flatMap { it.toolCallbacks.toList() }
            .map { MaskingToolCallback(it, piiMasker) }

    private val byName: Map<String, ToolCallback> =
        callbacks.associateBy { it.toolDefinition.name() }

    init {
        log.atInfo {
            message = "Wrapped MCP tool callbacks with PII masking"
            payload = mapOf("count" to callbacks.size)
        }
    }

    fun isEmpty(): Boolean = callbacks.isEmpty()

    fun contains(tool: String): Boolean = byName.containsKey(tool)

    fun call(
        tool: String,
        arguments: String,
    ): String {
        val callback =
            byName[tool] ?: throw IllegalArgumentException(
                "MCP server does not expose the tool $tool",
            )
        return callback.call(arguments)
    }
}
