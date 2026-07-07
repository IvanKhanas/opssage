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
package com.opssage.sre.metrics

import com.opssage.sre.config.CorrectnessProperties
import com.opssage.sre.config.MetricsProperties
import com.opssage.sre.config.QueryProperties
import com.opssage.sre.time.TimeWindow

import org.springframework.stereotype.Component

@Component
class CorrectnessQueryConfig(
    private val correctness: CorrectnessProperties,
    private val metrics: MetricsProperties,
    private val query: QueryProperties,
) {

    val invariantLabel: String
        get() = correctness.invariantLabel

    fun scope(
        service: String,
        namespace: String,
        window: TimeWindow,
    ): MetricScope =
        MetricScope.forWindow(
            service,
            namespace,
            window,
            query.maxPoints,
            query.minRateWindow.seconds,
        )

    fun promql(scope: MetricScope): String {
        val rate = "${scope.rateWindowSeconds}s"
        val labels =
            "${metrics.serviceLabel}=\"${scope.service}\"," +
                "${metrics.namespaceLabel}=\"${scope.namespace}\""
        val outcome = correctness.outcome
        val failed = "$labels,${outcome.label}=\"${outcome.failedValue}\""
        val total = rateSum(labels, rate)
        val failures = rateSum(failed, rate)
        return "(($failures) or ($total * 0)) / ($total)"
    }

    private fun rateSum(
        selector: String,
        rate: String,
    ): String =
        "sum by ($invariantLabel) " +
            "(rate(${correctness.metric}{$selector}[$rate]))"
}
