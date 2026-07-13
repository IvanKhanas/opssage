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

import com.opssage.agent.investigation.EvidenceGuard
import com.opssage.agent.investigation.GroundingContext
import com.opssage.agent.llm.LlmVerdict
import com.opssage.agent.model.AnchorWindow
import com.opssage.agent.model.Confidence
import com.opssage.agent.model.Observation
import com.opssage.agent.playbook.SreTools
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

import java.time.Instant

class EvidenceGuardTest {

    private val guard = EvidenceGuard()

    @Test
    fun `keeps evidence whose timestamps come from tool output`() {
        val verdict =
            verdict(
                summary = "Rollout at 2026-07-09T15:03:00Z degraded latency.",
                evidence = listOf("deployedAt=2026-07-09T15:03:00Z"),
            )

        val grounded = guard.verify(verdict, grounding(ROLLOUT_OUTPUT))

        assertThat(grounded.grounded).isTrue()
        assertThat(grounded.evidence).containsExactly(
            "deployedAt=2026-07-09T15:03:00Z",
        )
    }

    @Test
    fun `drops evidence with a timestamp the tools never returned`() {
        val verdict =
            verdict(
                summary = "Something happened.",
                evidence =
                    listOf(
                        "deployedAt=2026-07-09T17:25:00Z",
                        "errorRate=0.1994",
                    ),
            )

        val grounded = guard.verify(verdict, grounding(ROLLOUT_OUTPUT))

        assertThat(grounded.evidence).containsExactly("errorRate=0.1994")
        assertThat(grounded.grounded).isFalse()
    }

    @Test
    fun `marks a summary quoting an invented timestamp as ungrounded`() {
        val verdict =
            verdict(
                summary = "Раскатка в 2026-07-09T17:25:00Z сломала платежи.",
                evidence = listOf("errorRate=0.1994"),
            )

        val grounded = guard.verify(verdict, grounding(ROLLOUT_OUTPUT))

        assertThat(grounded.evidence).containsExactly("errorRate=0.1994")
        assertThat(grounded.grounded).isFalse()
    }

    @Test
    fun `accepts a timestamp reported at coarser precision`() {
        val verdict =
            verdict(
                summary = "First error at 2026-07-09T15:03:00Z.",
                evidence = emptyList(),
            )
        val observation =
            observation(
                """
                {
                  "firstSeen": "2026-07-09T15:03:00.482Z"
                }
                """.trimIndent(),
            )

        val grounded = guard.verify(verdict, grounding(observation))

        assertThat(grounded.grounded).isTrue()
    }

    @Test
    fun `accepts the anchored window bounds the server owns`() {
        val verdict =
            verdict(
                summary = "Окно расследования $FROM — $TO.",
                evidence = emptyList(),
            )

        val grounded = guard.verify(verdict, grounding(ROLLOUT_OUTPUT))

        assertThat(grounded.grounded).isTrue()
    }

    @Test
    fun `drops evidence quoting an invented trace id`() {
        val verdict =
            verdict(
                summary = "Trace analysis.",
                evidence =
                    listOf(
                        "traceId=$KNOWN_TRACE",
                        "traceId=deadbeefdeadbeefdeadbeefdeadbeef",
                    ),
            )

        val grounded = guard.verify(verdict, grounding(TRACE_OUTPUT))

        assertThat(grounded.evidence).containsExactly("traceId=$KNOWN_TRACE")
        assertThat(grounded.grounded).isFalse()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "error_rate вырос до 19.94% против 0.33% до раскатки",
            "p99 3.9s, узкое место acquirer-gateway POST /authorise",
            "PaymentGatewayTimeout доминирует среди 362 ошибок",
        ],
    )
    fun `never rejects reformatted numbers or free prose`(evidence: String) {
        val verdict = verdict(summary = "ok", evidence = listOf(evidence))

        val grounded = guard.verify(verdict, grounding(ROLLOUT_OUTPUT))

        assertThat(grounded.grounded).isTrue()
        assertThat(grounded.evidence).containsExactly(evidence)
    }

    @Test
    fun `ignores output of tool calls that failed`() {
        val failed =
            Observation(
                SreTools.GET_KUBERNETES_SERVICE_EVENTS,
                """
                {
                  "deployedAt": "2026-07-09T17:25:00Z"
                }
                """.trimIndent(),
                succeeded = false,
            )
        val verdict =
            verdict(
                summary = "Deploy at 2026-07-09T17:25:00Z.",
                evidence = emptyList(),
            )

        val grounded = guard.verify(verdict, grounding(failed))

        assertThat(grounded.grounded).isFalse()
    }

    private fun grounding(observation: Observation): GroundingContext =
        GroundingContext(
            listOf(observation),
            AnchorWindow(Instant.parse(FROM), Instant.parse(TO)),
        )

    private fun verdict(
        summary: String,
        evidence: List<String>,
    ): LlmVerdict = LlmVerdict(summary, Confidence.HIGH, evidence)

    private companion object {
        const val FROM = "2026-07-09T14:00:00Z"
        const val TO = "2026-07-09T16:00:00Z"
        const val KNOWN_TRACE = "11111111111111111111111111111111"

        val ROLLOUT_OUTPUT =
            observation(
                """
                {
                  "deployedAt": "2026-07-09T15:03:00Z",
                  "before": {
                    "errorRate": 0.0033
                  },
                  "after": {
                    "errorRate": 0.1994,
                    "latencyP99": 3.9
                  },
                  "topErrors": [
                    {
                      "fingerprint": "PaymentGatewayTimeout",
                      "count": 362
                    }
                  ],
                  "slowestSpan": "acquirer-gateway POST /authorise"
                }
                """.trimIndent(),
            )

        val TRACE_OUTPUT =
            observation(
                """
                {
                  "traces": [
                    {
                      "traceId": "$KNOWN_TRACE"
                    }
                  ]
                }
                """.trimIndent(),
            )

        fun observation(output: String): Observation =
            Observation(SreTools.GET_SERVICE_HEALTH, output, succeeded = true)
    }
}
