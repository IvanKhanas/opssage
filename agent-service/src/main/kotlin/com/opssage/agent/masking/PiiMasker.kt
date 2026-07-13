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
import io.github.oshai.kotlinlogging.KotlinLogging

import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class PiiMasker(
    private val properties: MaskingProperties,
    private val detectors: List<PiiDetector>,
) {

    fun mask(text: String): MaskingResult {
        if (!properties.enabled || text.isEmpty()) {
            return MaskingResult(text, masked = false)
        }
        val spans = detectors.flatMap { it.detect(text) }
        if (spans.isEmpty()) {
            return MaskingResult(text, masked = false)
        }
        val result = applySpans(text, PiiSpans.resolveOverlaps(spans))
        val masked = result != text
        if (masked) {
            log.debug { "Masked personal data before dispatch to external LLM" }
        }
        return MaskingResult(result, masked)
    }

    private fun applySpans(
        text: String,
        spans: List<PiiSpan>,
    ): String {
        val builder = StringBuilder(text)
        spans
            .sortedByDescending { it.start }
            .forEach { span ->
                builder.replace(span.start, span.endExclusive, span.token)
            }
        return builder.toString()
    }
}
