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

import com.opssage.agent.config.ConfidenceProperties
import com.opssage.agent.investigation.ConfidenceCalculator
import com.opssage.agent.model.Confidence
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ConfidenceCalculatorTest {

    private val calculator =
        ConfidenceCalculator(
            ConfidenceProperties(
                mediumEvidenceThreshold = 1,
                highEvidenceThreshold = 3,
            ),
        )

    @ParameterizedTest
    @CsvSource(
        "HIGH, 0, LOW",
        "HIGH, 1, MEDIUM",
        "HIGH, 3, HIGH",
        "MEDIUM, 5, MEDIUM",
        "LOW, 5, LOW",
    )
    fun `caps reported confidence by the amount of evidence`(
        reported: Confidence,
        evidenceCount: Int,
        expected: Confidence,
    ) {
        assertThat(calculator.reconcile(reported, evidenceCount))
            .isEqualTo(expected)
    }
}
