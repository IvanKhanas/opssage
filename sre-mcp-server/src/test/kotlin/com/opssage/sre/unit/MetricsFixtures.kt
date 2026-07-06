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

import com.opssage.sre.config.MetricsProperties
import com.opssage.sre.config.QueryProperties
import com.opssage.sre.model.MetricPoint
import com.opssage.sre.model.MetricSeries
import com.opssage.sre.time.TimeWindow

import java.time.Duration
import java.time.Instant

object MetricsFixtures {

    fun start(): Instant = Instant.parse("2026-06-27T10:00:00Z")

    fun window(): TimeWindow =
        TimeWindow(start(), Instant.parse("2026-06-27T11:00:00Z"))

    fun metricsProperties(): MetricsProperties =
        MetricsProperties(
            baseUrl = "http://vm",
            requestMetric = "http_server_requests_seconds_count",
            requestBucketMetric = "http_server_requests_seconds_bucket",
            serviceLabel = "service",
            namespaceLabel = "namespace",
            errorLabel = "outcome",
            errorOutcome = "SERVER_ERROR",
        )

    fun queryProperties(): QueryProperties =
        QueryProperties(
            defaultLookback = Duration.ofHours(2),
            maxWindow = Duration.ofHours(48),
            forwardBuffer = Duration.ofMinutes(10),
            maxPoints = 120,
            maxNewErrors = 20,
            maxTraces = 20,
            maxSpans = 200,
            maxPods = 50,
            maxEvents = 50,
            maxDependencies = 20,
            alertLogErrors = 5,
            minRateWindow = Duration.ofSeconds(60),
        )

    fun risingSeries(): MetricSeries =
        MetricSeries(
            name = "value",
            labels = emptyMap(),
            points =
                listOf(
                    MetricPoint(window().from, 1.0),
                    MetricPoint(window().to, 2.0),
                ),
        )
}
