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

import com.opssage.agent.config.MaskingProperties
import com.opssage.agent.masking.PiiDetector
import com.opssage.agent.masking.PiiMasker
import com.opssage.agent.masking.PiiSpan
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PiiMaskerCompositionTest {

    private val properties =
        MaskingProperties(
            enabled = true,
            emailToken = "[EMAIL]",
            ipToken = "[IP]",
            phoneToken = "[PHONE]",
            secretToken = "[REDACTED]",
            uuidToken = "[ID]",
            labelToken = "[PII]",
            nameToken = "[NAME]",
            maskFullNames = false,
            sensitiveLabels = emptyList(),
        )

    private fun detector(vararg spans: PiiSpan): PiiDetector =
        object : PiiDetector {
            override fun detect(text: String): List<PiiSpan> = spans.toList()
        }

    @Test
    fun `applies spans from multiple detectors`() {
        val text = "user Ivan from 10.0.0.1"
        val masker =
            PiiMasker(
                properties,
                listOf(
                    detector(PiiSpan(5, 9, "[NAME]")),
                    detector(PiiSpan(15, 23, "[IP]")),
                ),
            )

        val result = masker.mask(text)

        assertThat(result.text).isEqualTo("user [NAME] from [IP]")
        assertThat(result.masked).isTrue()
    }

    @Test
    fun `keeps the longer span when two detectors overlap`() {
        val text = "Ivan Ivanov reported"
        val masker =
            PiiMasker(
                properties,
                listOf(
                    detector(PiiSpan(0, 4, "[NAME]")),
                    detector(PiiSpan(0, 11, "[NAME]")),
                ),
            )

        val result = masker.mask(text)

        assertThat(result.text).isEqualTo("[NAME] reported")
    }

    @Test
    fun `returns text unchanged when detectors find nothing`() {
        val masker = PiiMasker(properties, listOf(detector()))

        val result = masker.mask("no personal data here")

        assertThat(result.masked).isFalse()
        assertThat(result.text).isEqualTo("no personal data here")
    }
}
