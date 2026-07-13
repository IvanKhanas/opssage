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
package com.opssage.agent.investigation

import com.opssage.agent.llm.LlmVerdict
import com.opssage.agent.model.AnchorWindow
import com.opssage.agent.model.Observation
import com.opssage.agent.text.Tokens
import io.github.oshai.kotlinlogging.KotlinLogging

import java.time.Instant
import java.time.temporal.ChronoUnit

import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

data class GroundingContext(
    val observations: List<Observation>,
    val window: AnchorWindow,
)

data class GroundedVerdict(
    val summary: String,
    val evidence: List<String>,
    val grounded: Boolean,
)

private data class Corpus(
    val text: String,
    val instants: Set<Instant>,
)

@Component
class EvidenceGuard {

    fun verify(
        verdict: LlmVerdict,
        grounding: GroundingContext,
    ): GroundedVerdict {
        val corpus = corpus(grounding)
        val dropped = verdict.evidence.filterNot { supports(corpus, it) }
        val summaryGaps = unsupported(corpus, verdict.summary)
        if (dropped.isNotEmpty() || summaryGaps.isNotEmpty()) {
            log.atWarn {
                message = "Model reported facts absent from tool output"
                payload =
                    mapOf(
                        "droppedEvidence" to dropped.size,
                        "summaryLiterals" to summaryGaps,
                    )
            }
        }
        return GroundedVerdict(
            summary = verdict.summary,
            evidence = verdict.evidence - dropped.toSet(),
            grounded = dropped.isEmpty() && summaryGaps.isEmpty(),
        )
    }

    private fun corpus(grounding: GroundingContext): Corpus {
        val observed =
            grounding.observations
                .filter(Observation::succeeded)
                .joinToString(SEPARATOR, transform = Observation::output)
        val text =
            observed + SEPARATOR + grounding.window.from +
                SEPARATOR + grounding.window.to
        return Corpus(text, instants(text))
    }

    private fun instants(text: String): Set<Instant> =
        Tokens.split(text).mapNotNullTo(mutableSetOf(), ::toInstant)

    private fun supports(
        corpus: Corpus,
        text: String,
    ): Boolean = unsupported(corpus, text).isEmpty()

    private fun unsupported(
        corpus: Corpus,
        text: String,
    ): List<String> =
        Tokens
            .split(text)
            .filter(::isVerifiable)
            .filterNot { token -> isSupported(corpus, token) }

    private fun isSupported(
        corpus: Corpus,
        token: String,
    ): Boolean {
        val instant = toInstant(token)
        if (instant != null) {
            return instant in corpus.instants
        }
        return corpus.text.contains(token, ignoreCase = true)
    }

    private fun isVerifiable(token: String): Boolean =
        toInstant(token) != null || isOpaqueId(token)

    private fun isOpaqueId(token: String): Boolean =
        token.length >= MIN_ID_LENGTH && token.all(::isHexDigit)

    private fun isHexDigit(symbol: Char): Boolean =
        symbol.digitToIntOrNull(HEX_RADIX) != null

    private fun toInstant(token: String): Instant? {
        if (token.length < MIN_INSTANT_LENGTH) {
            return null
        }
        return runCatching { Instant.parse(token) }
            .getOrNull()
            ?.truncatedTo(ChronoUnit.SECONDS)
    }

    private companion object {
        const val SEPARATOR = "\n"
        const val HEX_RADIX = 16
        const val MIN_ID_LENGTH = 16
        const val MIN_INSTANT_LENGTH = 20
    }
}
