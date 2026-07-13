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
import com.opssage.agent.masking.IncidentLiteralExtractor
import com.opssage.agent.masking.RegexPiiDetector
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class IncidentLiteralExtractorTest {

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
            maskFullNames = true,
            sensitiveLabels = listOf("userId", "orderId"),
            operationalIdPrefixes = listOf("ORD-", "u-"),
        )

    private val extractor =
        IncidentLiteralExtractor(
            properties,
            listOf(RegexPiiDetector(properties)),
        )

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "checkout fails for user@example.com|user@example.com",
            "orderId=A-4471 stuck in payment|A-4471",
            "irina@example.com cannot pay order ORD-88231|ORD-88231",
            "client u-77120 reports slow payment|u-77120",
            "trace 3f2504e0-4f89-11d3-9a0c-0305e82c3301 failed|" +
                "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
        ],
    )
    fun `extracts the first searchable literal from the raw input`(
        input: String,
        expected: String,
    ) {
        assertThat(extractor.firstLiteral(input)).isEqualTo(expected)
    }

    @Test
    fun `ignores personal names because they are not log search keys`() {
        assertThat(extractor.firstLiteral("Иван Петров жалуется на оплату"))
            .isNull()
    }

    @Test
    fun `returns null when the input carries no identifier`() {
        assertThat(extractor.firstLiteral("checkout is slow")).isNull()
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "cust:|client cust:4471 cannot pay|cust:4471",
            "order_|order_10023 is stuck|order_10023",
            "REQ-|REQ-77 failed downstream|REQ-77",
        ],
    )
    fun `recognises whatever id prefixes the deployment configures`(
        prefix: String,
        input: String,
        expected: String,
    ) {
        val configured =
            IncidentLiteralExtractor(
                properties.copy(operationalIdPrefixes = listOf(prefix)),
                listOf(RegexPiiDetector(properties)),
            )

        assertThat(configured.firstLiteral(input)).isEqualTo(expected)
    }

    @Test
    fun `falls back to pii literals when no prefixes are configured`() {
        val bare =
            IncidentLiteralExtractor(
                properties.copy(operationalIdPrefixes = emptyList()),
                listOf(RegexPiiDetector(properties)),
            )

        assertThat(bare.firstLiteral("irina@example.com order ORD-88231"))
            .isEqualTo("irina@example.com")
    }

    @Test
    fun `treats a blank prefix as no prefix at all`() {
        val blank =
            IncidentLiteralExtractor(
                properties.copy(operationalIdPrefixes = listOf("")),
                listOf(RegexPiiDetector(properties)),
            )

        assertThat(blank.firstLiteral("checkout is slow")).isNull()
    }

    @Test
    fun `ignores a bare prefix that carries no identifier`() {
        assertThat(extractor.firstLiteral("the ORD- prefix alone")).isNull()
    }

    @Test
    fun `keeps working when masking is disabled`() {
        val disabled = properties.copy(enabled = false)
        val extractorWithMaskingOff =
            IncidentLiteralExtractor(
                disabled,
                listOf(RegexPiiDetector(disabled)),
            )

        assertThat(extractorWithMaskingOff.firstLiteral("ping a@b.com"))
            .isEqualTo("a@b.com")
    }
}
