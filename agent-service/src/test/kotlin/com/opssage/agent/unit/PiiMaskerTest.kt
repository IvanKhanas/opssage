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
import com.opssage.agent.masking.PiiMasker
import com.opssage.agent.masking.RegexPiiDetector
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class PiiMaskerTest {

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
            sensitiveLabels = listOf("userId", "firstName", "фамилия", "card"),
        )

    private val masker =
        PiiMasker(properties, listOf(RegexPiiDetector(properties)))

    @ParameterizedTest
    @CsvSource(
        value = [
            "user reached john.doe@example.com now|[EMAIL]",
            "pod ip is 10.244.1.17 here|[IP]",
            "call +7 999 123 45 67 please|[PHONE]",
            "card=4111111111111111 leaked|[PII]",
            "session 550e8400-e29b-41d4-a716-446655440000 open|[ID]",
            "request from userId=98765 failed|[PII]",
            "checkout by firstName: Ivan stalled|[PII]",
            "жалоба от Иван Иванов поступила|[NAME]",
        ],
        delimiter = '|',
    )
    fun `masks personal data with the configured token`(
        input: String,
        token: String,
    ) {
        val result = masker.mask(input)

        assertThat(result.masked).isTrue()
        assertThat(result.text).contains(token)
    }

    @Test
    fun `keeps technical identifiers usable as evidence`() {
        val input =
            "traceId=11111111111111111111111111111111 " +
                "hostname: web-server-1 healthy"

        val result = masker.mask(input)

        assertThat(result.text)
            .contains("11111111111111111111111111111111")
        assertThat(result.text).contains("web-server-1")
    }

    @Test
    fun `leaves text without personal data untouched`() {
        val input = "checkout latency grew after the rollout"

        val result = masker.mask(input)

        assertThat(result.masked).isFalse()
        assertThat(result.text).isEqualTo(input)
    }

    @Test
    fun `does not leak the raw email once masked`() {
        val result = masker.mask("contact john.doe@example.com asap")

        assertThat(result.text).doesNotContain("john.doe@example.com")
    }

    @Test
    fun `returns text verbatim when masking is disabled`() {
        val disabledProps = properties.copy(enabled = false)
        val disabled =
            PiiMasker(disabledProps, listOf(RegexPiiDetector(disabledProps)))

        val result = disabled.mask("email john.doe@example.com")

        assertThat(result.masked).isFalse()
        assertThat(result.text).isEqualTo("email john.doe@example.com")
    }
}
