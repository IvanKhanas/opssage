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

import com.opssage.sre.metrics.MetricScope
import com.opssage.sre.metrics.PromQlTemplates
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PromQlTemplatesTest {

    @ParameterizedTest
    @ValueSource(
        strings = [
            """outcome="SERVER_ERROR"""",
            """code=~"5.."""",
            """status!="200",kind="server"""",
        ],
    )
    fun `splices whatever error matcher the deployment configures`(
        selector: String,
    ) {
        val templates =
            PromQlTemplates(
                MetricsFixtures
                    .metricsProperties()
                    .copy(errorSelector = selector),
            )

        val promql = templates.errorRate(scope())

        assertThat(promql).contains(
            """{service="checkout",namespace="prod",$selector}""",
        )
    }

    @Test
    fun `divides the error series by the unfiltered request series`() {
        val templates = PromQlTemplates(MetricsFixtures.metricsProperties())

        val promql = templates.errorRate(scope())

        assertThat(promql).isEqualTo(
            """sum(rate(http_server_requests_seconds_count""" +
                """{service="checkout",namespace="prod",""" +
                """outcome="SERVER_ERROR"}[60s])) / """ +
                """sum(rate(http_server_requests_seconds_count""" +
                """{service="checkout",namespace="prod"}[60s]))""",
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["{bad}", "outcome=\"x\"}", "a\nb"])
    fun `rejects an error selector that could escape the label matcher`(
        selector: String,
    ) {
        val properties =
            MetricsFixtures
                .metricsProperties()
                .copy(errorSelector = selector)

        assertThat(properties.hasContainedErrorSelector()).isFalse()
    }

    private fun scope(): MetricScope =
        MetricScope(
            service = "checkout",
            namespace = "prod",
            stepSeconds = 60,
            rateWindowSeconds = 60,
        )
}
