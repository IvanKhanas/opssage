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

import com.opssage.agent.catalog.ServiceCatalog
import com.opssage.agent.config.SreProperties
import com.opssage.agent.model.AnchorWindow
import com.opssage.agent.model.InvestigationType
import com.opssage.agent.model.Observation
import com.opssage.agent.playbook.AnalyticsPlaybookRunner
import com.opssage.agent.playbook.AnalyticsPrompt
import com.opssage.agent.playbook.AnalyticsRunRequest
import com.opssage.agent.playbook.AnalyticsScope
import com.opssage.agent.playbook.ComplaintAnalytics
import com.opssage.agent.playbook.FleetRanking
import com.opssage.agent.playbook.SreTools
import com.opssage.agent.playbook.ToolStep
import com.opssage.agent.playbook.ToolStepExecutor
import com.opssage.agent.tools.ToolOutputReader
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tools.jackson.module.kotlin.jacksonObjectMapper

import java.time.Duration
import java.time.Instant

@ExtendWith(MockKExtension::class)
class AnalyticsPlaybookRunnerTest {

    @MockK
    lateinit var catalog: ServiceCatalog

    @MockK
    lateinit var executor: ToolStepExecutor

    private val reader = ToolOutputReader(jacksonObjectMapper())

    private lateinit var runner: AnalyticsPlaybookRunner

    @BeforeEach
    fun setUp() {
        every { catalog.services() } returns SERVICES
        val properties = SreProperties("banking", Duration.ofMinutes(5))
        runner =
            AnalyticsPlaybookRunner(
                catalog,
                executor,
                FleetRanking(reader),
                ComplaintAnalytics(executor, reader, properties),
                properties,
            )
    }

    @Test
    fun `scans every catalog service for unresolved analytics`() {
        val steps = slot<List<ToolStep>>()
        every { executor.execute(capture(steps)) } returns emptyList()

        val observations =
            runner.run(
                request(
                    type = InvestigationType.ANALYTICAL_REQUEST,
                    input = "Какой сервис самый проблемный?",
                ),
            )

        assertThat(observations?.first()?.tool).isEqualTo("analyticsPlan")
        assertThat(steps.captured.map { it.arguments[SreTools.SERVICE] })
            .containsExactly(
                "payment-service",
                "payment-service",
                "payment-service",
                "deposit-service",
                "deposit-service",
                "deposit-service",
            )
    }

    @Test
    fun `compares every explicitly mentioned catalog service`() {
        val steps = slot<List<ToolStep>>()
        every { executor.execute(capture(steps)) } returns emptyList()

        runner.run(
            request(
                type = InvestigationType.ANALYTICAL_REQUEST,
                input =
                    "Сравни ошибки payment-service и deposit-service.",
            ),
        )

        assertThat(steps.captured.map { it.arguments[SreTools.SERVICE] })
            .contains("payment-service", "deposit-service")
        assertThat(steps.captured.map { it.tool })
            .contains(SreTools.FIND_SERVICE_TRACES)
    }

    @Test
    fun `prepends a code-computed ranking to the fleet scan observations`() {
        every { executor.execute(any()) } returns
            listOf(
                Observation(SreTools.GET_SERVICE_HEALTH, HEALTH_JSON, true),
            )

        val observations =
            runner.run(
                request(
                    type = InvestigationType.ANALYTICAL_REQUEST,
                    input = "Какой сервис самый проблемный?",
                ),
            )

        assertThat(observations?.map { it.tool })
            .containsExactly(
                "analyticsPlan",
                "analyticsRanking",
                SreTools.GET_SERVICE_HEALTH,
            )
    }

    @Test
    fun `probes the complaint literal across the catalog first`() {
        val steps = mutableListOf<List<ToolStep>>()
        every { executor.execute(capture(steps)) } returns emptyList()

        runner.run(complaint())

        val probes = steps.first()
        assertThat(probes.map { it.tool }).containsExactly(
            SreTools.FIND_LOG_ERRORS_BY_TEXT,
            SreTools.FIND_USER_RELATED_TRACES,
            SreTools.FIND_LOG_ERRORS_BY_TEXT,
            SreTools.FIND_USER_RELATED_TRACES,
        )
        val searches =
            probes.filter { it.tool == SreTools.FIND_LOG_ERRORS_BY_TEXT }
        assertThat(searches).allSatisfy { step ->
            assertThat(step.arguments)
                .containsEntry(SreTools.QUERY, "ORD-88231")
        }
    }

    @Test
    fun `drills only into the service the literal matched`() {
        val steps = mutableListOf<List<ToolStep>>()
        every { executor.execute(capture(steps)) } returnsMany
            listOf(
                listOf(
                    Observation(
                        SreTools.FIND_LOG_ERRORS_BY_TEXT,
                        MATCHED_LOGS,
                        true,
                    ),
                    Observation(
                        SreTools.FIND_USER_RELATED_TRACES,
                        NO_TRACES,
                        true,
                    ),
                    Observation(
                        SreTools.FIND_LOG_ERRORS_BY_TEXT,
                        NO_MATCHES,
                        true,
                    ),
                    Observation(
                        SreTools.FIND_USER_RELATED_TRACES,
                        NO_TRACES,
                        true,
                    ),
                ),
                emptyList(),
            )

        runner.run(complaint())

        val followUp = steps.last()
        assertThat(followUp.map { it.arguments[SreTools.SERVICE] })
            .containsOnly("payment-service")
        assertThat(followUp.map { it.tool }).containsExactly(
            SreTools.GET_SERVICE_HEALTH,
            SreTools.FIND_TOP_LOG_ERRORS,
            SreTools.GET_SERVICE_CORRECTNESS,
            SreTools.FIND_SERVICE_TRACES,
        )
    }

    @Test
    fun `never widens to fleet errors when nothing matches the user`() {
        val steps = mutableListOf<List<ToolStep>>()
        every { executor.execute(capture(steps)) } returns
            List(4) {
                Observation(SreTools.FIND_LOG_ERRORS_BY_TEXT, NO_MATCHES, true)
            }

        val observations = runner.run(complaint())

        assertThat(steps).hasSize(1)
        assertThat(observations?.map { it.tool })
            .doesNotContain(SreTools.FIND_TOP_LOG_ERRORS)
        assertThat(observations?.first()?.output)
            .contains("nothing matched the user")
    }

    private fun complaint(): AnalyticsRunRequest =
        request(
            type = InvestigationType.USER_PROBLEM_INVESTIGATION,
            input = "ORD-88231 failed",
            literal = "ORD-88231",
        )

    private fun request(
        type: InvestigationType,
        input: String,
        literal: String? = null,
    ): AnalyticsRunRequest =
        AnalyticsRunRequest(
            AnalyticsScope(type, target = null),
            WINDOW,
            AnalyticsPrompt(input, literal),
        )

    private companion object {
        val SERVICES = listOf("payment-service", "deposit-service")

        val MATCHED_LOGS =
            """
            {
              "topErrors": [
                {
                  "count": 2
                }
              ]
            }
            """.trimIndent()

        val NO_MATCHES =
            """
            {
              "topErrors": []
            }
            """.trimIndent()

        val NO_TRACES =
            """
            {
              "traces": []
            }
            """.trimIndent()

        val HEALTH_JSON =
            """
            {
              "service": "payment-service",
              "signals": [
                {
                  "metricName": "error_rate",
                  "stats": {
                    "latest": 0.2
                  }
                }
              ]
            }
            """.trimIndent()

        val WINDOW =
            AnchorWindow(
                Instant.parse("2026-07-09T10:00:00Z"),
                Instant.parse("2026-07-09T12:00:00Z"),
            )
    }
}
