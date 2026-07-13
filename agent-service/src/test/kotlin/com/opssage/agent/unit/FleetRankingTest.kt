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

import com.opssage.agent.model.Observation
import com.opssage.agent.playbook.FleetRanking
import com.opssage.agent.playbook.ServiceScore
import com.opssage.agent.playbook.SreTools
import com.opssage.agent.tools.ToolOutputReader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper

class FleetRankingTest {

    private val ranking = FleetRanking(ToolOutputReader(jacksonObjectMapper()))

    @Test
    fun `orders services by error rate before the model sees them`() {
        val scores =
            ranking.rank(
                listOf(
                    health("deposit-service", 0.0029, 0.501),
                    health("payment-service", 0.2065, 4.722),
                    health("fraud-service", 0.006, 3.7),
                ),
            )

        assertThat(scores.map(ServiceScore::service))
            .containsExactly(
                "payment-service",
                "fraud-service",
                "deposit-service",
            )
    }

    @Test
    fun `merges health logs and correctness into one score per service`() {
        val scores =
            ranking.rank(
                listOf(
                    health("payment-service", 0.2065, 4.722),
                    topErrors("payment-service", 161, 20),
                    correctness("payment-service", 0.28),
                ),
            )

        assertThat(scores).containsExactly(
            ServiceScore("payment-service", 0.2065, 4.722, 181, 0.28),
        )
    }

    @Test
    fun `breaks error rate ties by the number of error log lines`() {
        val scores =
            ranking.rank(
                listOf(
                    health("a-service", 0.01, 0.2),
                    topErrors("a-service", 1, 1),
                    health("b-service", 0.01, 0.1),
                    topErrors("b-service", 50, 50),
                ),
            )

        assertThat(scores.map(ServiceScore::service))
            .containsExactly("b-service", "a-service")
    }

    @Test
    fun `ranks services without metric data last`() {
        val scores =
            ranking.rank(
                listOf(
                    Observation(
                        SreTools.GET_SERVICE_HEALTH,
                        """{"service":"quiet-service","signals":[]}""",
                        succeeded = true,
                    ),
                    health("payment-service", 0.2065, 4.722),
                ),
            )

        assertThat(scores.map(ServiceScore::service))
            .containsExactly("payment-service", "quiet-service")
    }

    @Test
    fun `ignores failed steps and unrelated tools`() {
        val scores =
            ranking.rank(
                listOf(
                    Observation(
                        SreTools.GET_SERVICE_HEALTH,
                        "Инструмент недоступен",
                        succeeded = false,
                    ),
                    Observation(
                        SreTools.FIND_SERVICE_TRACES,
                        """{"service":"trace-only-service","traces":[]}""",
                        succeeded = true,
                    ),
                ),
            )

        assertThat(scores).isEmpty()
    }

    @Test
    fun `emits a single ranking observation the model must not reorder`() {
        val observations =
            ranking.observe(
                listOf(
                    health("deposit-service", 0.0029, 0.501),
                    health("payment-service", 0.2065, 4.722),
                ),
            )

        assertThat(observations).hasSize(1)
        assertThat(observations.first().tool).isEqualTo("analyticsRanking")
        assertThat(observations.first().output)
            .contains("1. payment-service error_rate=0.2065 p99=4.722s")
            .contains("2. deposit-service error_rate=0.0029 p99=0.501s")
    }

    @Test
    fun `emits nothing when no service reported telemetry`() {
        assertThat(ranking.observe(emptyList())).isEmpty()
    }

    private fun health(
        service: String,
        errorRate: Double,
        latencyP99: Double,
    ): Observation =
        Observation(
            SreTools.GET_SERVICE_HEALTH,
            """
            {
              "service": "$service",
              "signals": [
                {"metricName": "error_rate", "stats": {"latest": $errorRate}},
                {"metricName": "latency_p95", "stats": null},
                {"metricName": "latency_p99", "stats": {"latest": $latencyP99}}
              ]
            }
            """.trimIndent(),
            succeeded = true,
        )

    private fun topErrors(
        service: String,
        first: Long,
        second: Long,
    ): Observation =
        Observation(
            SreTools.FIND_TOP_LOG_ERRORS,
            """
            {
              "service": "$service",
              "topErrors": [{"count": $first}, {"count": $second}]
            }
            """.trimIndent(),
            succeeded = true,
        )

    private fun correctness(
        service: String,
        failureRatio: Double,
    ): Observation =
        Observation(
            SreTools.GET_SERVICE_CORRECTNESS,
            """
            {
              "service": "$service",
              "invariants": [
                {"latestFailureRatio": 0.01},
                {"latestFailureRatio": $failureRatio}
              ]
            }
            """.trimIndent(),
            succeeded = true,
        )
}
