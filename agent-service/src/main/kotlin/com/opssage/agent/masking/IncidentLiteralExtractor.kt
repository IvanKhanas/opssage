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

import com.opssage.agent.config.MaskingProperties
import com.opssage.agent.text.Tokens

import org.springframework.stereotype.Component

@Component
class IncidentLiteralExtractor(
    private val properties: MaskingProperties,
    private val detectors: List<PiiDetector>,
) {

    private val searchableTokens: Set<String> =
        setOf(
            properties.emailToken,
            properties.uuidToken,
            properties.labelToken,
        )

    fun firstLiteral(text: String): String? {
        if (text.isEmpty()) {
            return null
        }
        operationalLiteral(text)?.let { return it }
        val spans = detectors.flatMap { it.detect(text) }
        return PiiSpans
            .resolveOverlaps(spans)
            .firstOrNull { it.token in searchableTokens }
            ?.let { text.substring(it.start, it.endExclusive) }
    }

    private val idPrefixes: List<String> =
        properties.operationalIdPrefixes.filter(String::isNotBlank)

    private fun operationalLiteral(text: String): String? {
        if (idPrefixes.isEmpty()) {
            return null
        }
        return Tokens.split(text).firstOrNull { token ->
            idPrefixes.any { prefix -> carriesId(token, prefix) }
        }
    }

    private fun carriesId(
        token: String,
        prefix: String,
    ): Boolean =
        token.length > prefix.length &&
            token.startsWith(prefix, ignoreCase = true)
}
