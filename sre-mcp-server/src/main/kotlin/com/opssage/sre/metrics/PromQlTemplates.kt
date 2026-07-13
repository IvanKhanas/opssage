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

import com.opssage.sre.config.MetricsProperties

import org.springframework.stereotype.Component

@Component
class PromQlTemplates(
    private val metrics: MetricsProperties,
) {

    fun requestRate(scope: MetricScope): String =
        rateSum(metrics.requestMetric, selector(scope), scope)

    fun errorRate(scope: MetricScope): String =
        "${rateSum(metrics.requestMetric, errorSelector(scope), scope)} / " +
            requestRate(scope)

    fun latencyQuantile(
        scope: MetricScope,
        quantile: Double,
    ): String =
        "histogram_quantile($quantile, " +
            "${rateSum(metrics.requestBucketMetric, selector(scope), scope)} " +
            "by (le))"

    private fun rateSum(
        metric: String,
        selector: String,
        scope: MetricScope,
    ): String = "sum(rate($metric$selector[${rate(scope)}]))"

    private fun selector(scope: MetricScope): String = "{${baseLabels(scope)}}"

    private fun errorSelector(scope: MetricScope): String =
        "{${baseLabels(scope)},${metrics.errorSelector}}"

    private fun baseLabels(scope: MetricScope): String =
        "${metrics.serviceLabel}=\"${scope.service}\"," +
            "${metrics.namespaceLabel}=\"${scope.namespace}\""

    private fun rate(scope: MetricScope): String = "${scope.rateWindowSeconds}s"

    companion object {
        const val P95 = 0.95
        const val P99 = 0.99
    }
}
