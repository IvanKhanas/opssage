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
package com.opssage.agent.llm

import com.opssage.agent.model.Confidence
import io.github.oshai.kotlinlogging.KotlinLogging
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class LlmVerdictParser(
    private val mapper: ObjectMapper,
) {

    fun parse(content: String?): LlmVerdict? {
        val json = JsonPayloads.extractObject(content) ?: return null
        val parsed =
            runCatching {
                mapper.readValue(json, LlmVerdict::class.java)
            }
        parsed.getOrNull()?.let { return it }
        parseLenient(json)?.let { return it }
        parsed.exceptionOrNull()?.let { error ->
            log.atWarn {
                message = "Model output is not valid JSON"
                payload = mapOf("outputChars" to json.length)
                cause = error
            }
        }
        return null
    }

    private fun parseLenient(json: String): LlmVerdict? =
        runCatching {
            val root =
                mapper
                    .readTree(json)
                    .takeIf(JsonNode::isObject)
                    ?: return null
            val summary =
                root
                    .get("summary")
                    ?.let(::nodeText)
                    ?: return null
            val confidence = parseConfidence(root.get("confidence"))
            val evidence = parseEvidence(root.get("evidence"))
            LlmVerdict(summary, confidence, evidence)
        }.getOrNull()

    private fun parseConfidence(node: JsonNode?): Confidence {
        val value = node?.takeIf(JsonNode::isString)?.asString()
        return runCatching { Confidence.valueOf(value.orEmpty()) }
            .getOrDefault(Confidence.LOW)
    }

    private fun parseEvidence(node: JsonNode?): List<String> {
        if (node?.isArray != true) {
            return emptyList()
        }
        return node.mapNotNull(::evidenceText)
    }

    private fun evidenceText(node: JsonNode): String? {
        if (node.isString) {
            return node.asString()
        }
        if (!node.isObject) {
            return nodeText(node)
        }
        val signal = node.get("signal")?.takeIf(JsonNode::isString)?.asString()
        val details = node.get("details")?.let(::nodeText)
        return listOfNotNull(signal, details)
            .joinToString(": ")
            .takeIf(String::isNotBlank)
            ?: nodeText(node)
    }

    private fun nodeText(node: JsonNode): String? =
        when {
            node.isString -> node.asString()
            node.isObject -> objectText(node)
            node.isArray -> arrayText(node)
            node.isNull -> null
            else -> node.asString()
        }

    private fun objectText(node: JsonNode): String =
        node
            .properties()
            .joinToString("; ") { (key, value) ->
                "$key: ${nodeText(value).orEmpty()}"
            }

    private fun arrayText(node: JsonNode): String =
        node
            .mapNotNull(::nodeText)
            .joinToString("; ")
}
