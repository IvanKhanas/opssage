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
package com.opssage.agent.unit

import com.opssage.agent.llm.LlmVerdictParser
import com.opssage.agent.model.Confidence
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import tools.jackson.module.kotlin.jacksonObjectMapper

class LlmVerdictParserTest {

    private val parser = LlmVerdictParser(jacksonObjectMapper())

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["", "   ", "no braces here", "}{"])
    fun `returns null when there is no json object`(content: String?) {
        assertThat(parser.parse(content)).isNull()
    }

    @Test
    fun `parses a strict schema verdict`() {
        val verdict =
            parser.parse(
                """
                {
                  "summary": "root cause",
                  "confidence": "HIGH",
                  "evidence": [
                    "log a",
                    "metric b"
                  ]
                }
                """.trimIndent(),
            )

        assertThat(verdict).isNotNull
        assertThat(verdict!!.summary).isEqualTo("root cause")
        assertThat(verdict.confidence).isEqualTo(Confidence.HIGH)
        assertThat(verdict.evidence).containsExactly("log a", "metric b")
    }

    @Test
    fun `extracts a json object embedded in surrounding prose`() {
        val verdict =
            parser.parse(
                "Вот итог: {\"summary\":\"ok\",\"confidence\":\"LOW\"," +
                    "\"evidence\":[]} — конец.",
            )

        assertThat(verdict?.summary).isEqualTo("ok")
    }

    @Test
    fun `returns null when the object carries no summary`() {
        assertThat(parser.parse("""{"note":"no summary here"}""")).isNull()
    }

    @Test
    fun `flattens an object summary and object evidence`() {
        val verdict =
            parser.parse(
                """
                {
                  "summary": {
                    "service": "deposit-service",
                    "conclusion": "healthy"
                  },
                  "confidence": "HIGH",
                  "evidence": [
                    {"signal": "metrics", "details": "p99 is stable"}
                  ]
                }
                """.trimIndent(),
            )

        assertThat(verdict).isNotNull
        assertThat(verdict!!.summary)
            .contains("service: deposit-service")
            .contains("conclusion: healthy")
        assertThat(verdict.confidence).isEqualTo(Confidence.HIGH)
        assertThat(verdict.evidence).containsExactly("metrics: p99 is stable")
    }

    @Test
    fun `flattens nested arrays and stringifies scalar evidence`() {
        val verdict =
            parser.parse(
                """
                {
                  "summary": ["first", "second"],
                  "confidence": "MEDIUM",
                  "evidence": [42, {"free": "form"}]
                }
                """.trimIndent(),
            )

        assertThat(verdict).isNotNull
        assertThat(verdict!!.summary).isEqualTo("first; second")
        assertThat(verdict.confidence).isEqualTo(Confidence.MEDIUM)
        assertThat(verdict.evidence).containsExactly("42", "free: form")
    }

    @ParameterizedTest
    @ValueSource(strings = ["not-a-level", ""])
    fun `defaults confidence to LOW when it is invalid`(level: String) {
        val verdict =
            parser.parse(
                """
                {
                  "summary": {
                    "k": "v"
                  },
                  "confidence": "$level",
                  "evidence": "not an array"
                }
                """.trimIndent(),
            )

        assertThat(verdict).isNotNull
        assertThat(verdict!!.confidence).isEqualTo(Confidence.LOW)
        assertThat(verdict.evidence).isEmpty()
    }

    @Test
    fun `defaults confidence to LOW when it is missing`() {
        val verdict =
            parser.parse(
                """
                {
                  "summary": {
                    "k": "v"
                  }
                }
                """.trimIndent(),
            )

        assertThat(verdict?.confidence).isEqualTo(Confidence.LOW)
    }
}
