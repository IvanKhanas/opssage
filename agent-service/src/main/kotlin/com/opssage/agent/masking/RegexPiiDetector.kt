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

import org.springframework.stereotype.Component

@Component
class RegexPiiDetector(
    private val properties: MaskingProperties,
) : PiiDetector {

    private val labelPatterns: List<Regex> =
        properties.sensitiveLabels.map { label ->
            Regex(
                "(?iU)\\b" + Regex.escape(label) + "\\b\\s*[:=]\\s*\"?" +
                    "([^\\s\",;}]+)",
            )
        }

    override fun detect(text: String): List<PiiSpan> {
        val spans = mutableListOf<PiiSpan>()
        valuePatterns().forEach { (pattern, token) ->
            pattern.findAll(text).forEach { match ->
                spans += PiiSpan(match.range.first, match.range.last + 1, token)
            }
        }
        labelPatterns.forEach { pattern ->
            pattern.findAll(text).forEach { match ->
                val value = match.groups[1] ?: return@forEach
                spans +=
                    PiiSpan(
                        value.range.first,
                        value.range.last + 1,
                        properties.labelToken,
                    )
            }
        }
        if (properties.maskFullNames) {
            CYRILLIC_FULL_NAME.findAll(text).forEach { match ->
                spans +=
                    PiiSpan(
                        match.range.first,
                        match.range.last + 1,
                        properties.nameToken,
                    )
            }
        }
        return spans
    }

    private fun valuePatterns(): List<Pair<Regex, String>> =
        listOf(
            EMAIL to properties.emailToken,
            IPV4 to properties.ipToken,
            UUID to properties.uuidToken,
            PHONE to properties.phoneToken,
        )

    private companion object {
        val EMAIL =
            Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
        val IPV4 =
            Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")
        val UUID =
            Regex(
                "\\b[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}\\b",
            )
        val PHONE =
            Regex(
                "(?<!\\d)(?:\\+\\d[\\d ()-]{9,}\\d|" +
                    "\\d[\\d ()-]*[ ()-][\\d ()-]{7,}\\d)(?!\\d)",
            )
        val CYRILLIC_FULL_NAME =
            Regex("(?U)\\b[А-ЯЁ][а-яё]+\\s+[А-ЯЁ][а-яё]+\\b")
    }
}
