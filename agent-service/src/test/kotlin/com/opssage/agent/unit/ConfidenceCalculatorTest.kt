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
import com.opssage.agent.investigation.ConfidenceInputs
import com.opssage.agent.model.Confidence
import com.opssage.agent.model.InvestigationType
import com.opssage.agent.model.Observation
import com.opssage.agent.playbook.SreTools
import com.opssage.agent.tools.ToolOutputReader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import tools.jackson.module.kotlin.jacksonObjectMapper

class ConfidenceCalculatorTest {

    private val calculator =
        ConfidenceCalculator(
            ConfidenceProperties(
                mediumEvidenceThreshold = 1,
                highEvidenceThreshold = 3,
            ),
            ToolOutputReader(jacksonObjectMapper()),
        )

    @ParameterizedTest
    @CsvSource(
        "HIGH, 0, LOW",
        "HIGH, 1, MEDIUM",
        "HIGH, 3, MEDIUM",
        "MEDIUM, 5, MEDIUM",
        "LOW, 5, LOW",
    )
    fun `caps confidence by evidence and by missing trace confirmation`(
        reported: Confidence,
        evidenceCount: Int,
        expected: Confidence,
    ) {
        val confidence = calculator.reconcile(inputs(reported, evidenceCount))

        assertThat(confidence).isEqualTo(expected)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            SreTools.FIND_SERVICE_TRACES,
            SreTools.FIND_USER_RELATED_TRACES,
        ],
    )
    fun `allows high confidence once traces confirm the chain`(tool: String) {
        val confidence =
            calculator.reconcile(
                inputs(
                    Confidence.HIGH,
                    evidenceCount = 3,
                    observations = listOf(Observation(tool, TRACES, true)),
                ),
            )

        assertThat(confidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun `keeps high confidence capped when the trace lookup found nothing`() {
        val confidence =
            calculator.reconcile(
                inputs(
                    Confidence.HIGH,
                    evidenceCount = 3,
                    observations =
                        listOf(
                            Observation(
                                SreTools.FIND_SERVICE_TRACES,
                                NO_TRACES,
                                succeeded = true,
                            ),
                        ),
                ),
            )

        assertThat(confidence).isEqualTo(Confidence.MEDIUM)
    }

    @Test
    fun `keeps high confidence capped when the trace lookup failed`() {
        val confidence =
            calculator.reconcile(
                inputs(
                    Confidence.HIGH,
                    evidenceCount = 3,
                    observations =
                        listOf(
                            Observation(
                                SreTools.FIND_SERVICE_TRACES,
                                TRACES,
                                succeeded = false,
                            ),
                        ),
                ),
            )

        assertThat(confidence).isEqualTo(Confidence.MEDIUM)
    }

    @Test
    fun `drops a user complaint to low when nothing links to the user`() {
        val confidence =
            calculator.reconcile(
                ConfidenceInputs(
                    type = InvestigationType.USER_PROBLEM_INVESTIGATION,
                    reported = Confidence.HIGH,
                    evidenceCount = 6,
                    observations =
                        listOf(
                            Observation(
                                SreTools.FIND_TOP_LOG_ERRORS,
                                FLEET_ERRORS,
                                succeeded = true,
                            ),
                            Observation(
                                SreTools.FIND_USER_RELATED_TRACES,
                                NO_TRACES,
                                succeeded = true,
                            ),
                            Observation(
                                SreTools.FIND_LOG_ERRORS_BY_TEXT,
                                NO_MATCHES,
                                succeeded = true,
                            ),
                        ),
                ),
            )

        assertThat(confidence).isEqualTo(Confidence.LOW)
    }

    @Test
    fun `keeps a user complaint confident when the literal matched a log`() {
        val confidence =
            calculator.reconcile(
                ConfidenceInputs(
                    type = InvestigationType.USER_PROBLEM_INVESTIGATION,
                    reported = Confidence.MEDIUM,
                    evidenceCount = 3,
                    observations =
                        listOf(
                            Observation(
                                SreTools.FIND_LOG_ERRORS_BY_TEXT,
                                FLEET_ERRORS,
                                succeeded = true,
                            ),
                        ),
                ),
            )

        assertThat(confidence).isEqualTo(Confidence.MEDIUM)
    }

    @Test
    fun `raises low model confidence when tools returned evidence`() {
        val confidence =
            calculator.reconcile(
                inputs(
                    Confidence.LOW,
                    evidenceCount = 5,
                    observations =
                        listOf(
                            Observation(
                                SreTools.FIND_TOP_LOG_ERRORS,
                                """{"confidence": "HIGH"}""",
                                succeeded = true,
                            ),
                        ),
                ),
            )

        assertThat(confidence).isEqualTo(Confidence.MEDIUM)
    }

    @Test
    fun `does not raise confidence when all tool data is weak`() {
        val confidence =
            calculator.reconcile(
                inputs(
                    Confidence.LOW,
                    evidenceCount = 5,
                    observations =
                        listOf(
                            Observation(
                                SreTools.FIND_TOP_LOG_ERRORS,
                                """{"confidence": "LOW"}""",
                                succeeded = true,
                            ),
                        ),
                ),
            )

        assertThat(confidence).isEqualTo(Confidence.LOW)
    }

    @Test
    fun `reads tool confidence from mcp content arrays`() {
        val confidence =
            calculator.reconcile(
                inputs(
                    Confidence.LOW,
                    evidenceCount = 5,
                    observations =
                        listOf(
                            Observation(
                                SreTools.FIND_TOP_LOG_ERRORS,
                                CONTENT_ARRAY,
                                succeeded = true,
                            ),
                        ),
                ),
            )

        assertThat(confidence).isEqualTo(Confidence.MEDIUM)
    }

    @ParameterizedTest
    @EnumSource(Confidence::class)
    fun `collapses to low when a reported fact is not in tool output`(
        reported: Confidence,
    ) {
        val confidence =
            calculator.reconcile(
                inputs(reported, evidenceCount = 5).copy(grounded = false),
            )

        assertThat(confidence).isEqualTo(Confidence.LOW)
    }

    private fun inputs(
        reported: Confidence,
        evidenceCount: Int,
        observations: List<Observation> = emptyList(),
    ): ConfidenceInputs =
        ConfidenceInputs(
            type = InvestigationType.ANALYTICAL_REQUEST,
            reported = reported,
            evidenceCount = evidenceCount,
            observations = observations,
        )

    private companion object {
        val TRACES =
            """
            {
              "traces": [
                {
                  "traceId": "abc",
                  "durationMs": 4200.0
                }
              ]
            }
            """.trimIndent()

        val NO_TRACES =
            """
            {
              "traces": [],
              "confidence": "LOW"
            }
            """.trimIndent()

        val NO_MATCHES =
            """
            {
              "topErrors": [],
              "confidence": "LOW"
            }
            """.trimIndent()

        val FLEET_ERRORS =
            """
            {
              "topErrors": [
                {
                  "count": 240
                }
              ]
            }
            """.trimIndent()

        val CONTENT_ARRAY =
            """
            [
              {
                "type": "text",
                "text": "{\"confidence\":\"HIGH\"}"
              }
            ]
            """.trimIndent()
    }
}
