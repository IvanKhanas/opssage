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

import com.opssage.agent.config.NerProperties
import com.opssage.agent.masking.OnnxNerPiiDetector
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class OnnxNerPiiDetectorTest {

    @Test
    fun `fails fast with an actionable message when the model path is blank`() {
        val properties =
            NerProperties(
                enabled = true,
                modelPath = "",
                scoreThreshold = 0.5,
                entityTokens = mapOf("PER" to "[NAME]"),
                maxConcurrentInferences = 4,
            )

        assertThatThrownBy { OnnxNerPiiDetector(properties) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("agent.masking.ner.model-path")
    }
}
