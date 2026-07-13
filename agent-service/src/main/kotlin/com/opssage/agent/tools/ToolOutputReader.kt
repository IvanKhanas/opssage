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
package com.opssage.agent.tools

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

import org.springframework.stereotype.Component

@Component
class ToolOutputReader(
    private val mapper: ObjectMapper,
) {

    fun read(output: String): JsonNode? = tree(output)?.let(::unwrap)

    fun text(
        node: JsonNode,
        field: String,
    ): String? =
        node
            .get(field)
            ?.takeIf(JsonNode::isString)
            ?.asString()

    fun number(
        node: JsonNode,
        field: String,
    ): Double? =
        node
            .get(field)
            ?.takeIf(JsonNode::isNumber)
            ?.asDouble()

    fun array(
        node: JsonNode,
        field: String,
    ): List<JsonNode> =
        node
            .get(field)
            ?.takeIf(JsonNode::isArray)
            ?.toList()
            .orEmpty()

    fun hasEntries(
        output: String,
        field: String,
    ): Boolean = read(output)?.let { array(it, field).isNotEmpty() } == true

    private fun tree(output: String): JsonNode? =
        runCatching { mapper.readTree(output) }.getOrNull()

    private fun unwrap(node: JsonNode): JsonNode? =
        when {
            node.isObject -> node
            node.isArray -> node.firstNotNullOfOrNull(::content)
            else -> null
        }

    private fun content(item: JsonNode): JsonNode? {
        if (!item.isObject) {
            return null
        }
        val embedded = text(item, TEXT_FIELD) ?: return item
        return tree(embedded)?.takeIf(JsonNode::isObject)
    }

    private companion object {
        const val TEXT_FIELD = "text"
    }
}
