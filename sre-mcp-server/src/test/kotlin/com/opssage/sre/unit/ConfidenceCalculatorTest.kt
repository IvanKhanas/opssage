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
package com.opssage.sre.unit

import com.opssage.sre.model.Confidence
import com.opssage.sre.util.ConfidenceCalculator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ConfidenceCalculatorTest {

    @ParameterizedTest
    @CsvSource(
        "0, 0, LOW",
        "0, 3, LOW",
        "2, 3, MEDIUM",
        "3, 3, HIGH",
    )
    fun `maps data completeness to confidence`(
        withData: Int,
        total: Int,
        expected: Confidence,
    ) {
        assertThat(ConfidenceCalculator.of(withData, total))
            .isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "0, 500, LOW",
        "250, 500, HIGH",
        "500, 500, MEDIUM",
        "501, 500, MEDIUM",
    )
    fun `treats a reached sample limit as reduced confidence`(
        sampleCount: Int,
        maxSamples: Int,
        expected: Confidence,
    ) {
        assertThat(ConfidenceCalculator.ofSamples(sampleCount, maxSamples))
            .isEqualTo(expected)
    }
}
