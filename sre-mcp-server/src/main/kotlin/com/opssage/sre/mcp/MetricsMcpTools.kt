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
package com.opssage.sre.mcp

import com.opssage.sre.config.McpProperties
import com.opssage.sre.dto.DependencyImpactResult
import com.opssage.sre.dto.RolloutComparisonResult
import com.opssage.sre.dto.ServiceCatalogResult
import com.opssage.sre.dto.ServiceHealthResult
import com.opssage.sre.metrics.DependencyImpactQuery
import com.opssage.sre.metrics.DependencyQuery
import com.opssage.sre.metrics.RolloutComparisonQuery
import com.opssage.sre.metrics.RolloutQuery
import com.opssage.sre.metrics.ServiceCatalogQuery
import com.opssage.sre.metrics.ServiceHealthQuery
import com.opssage.sre.time.TimeWindowResolver
import com.opssage.sre.util.Identifiers
import com.opssage.sre.util.ToolInputs
import com.opssage.sre.util.blockingGet

import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component

@Component
class MetricsMcpTools(
    private val serviceHealthQuery: ServiceHealthQuery,
    private val rolloutComparisonQuery: RolloutComparisonQuery,
    private val dependencyImpactQuery: DependencyImpactQuery,
    private val serviceCatalogQuery: ServiceCatalogQuery,
    private val resolver: TimeWindowResolver,
    private val mcp: McpProperties,
) : McpToolSet {

    @Tool(
        description =
            "List the services that actually report telemetry, taken from " +
                "the service label observed in the metrics store. These are " +
                "exactly the names the other tools accept; a name outside " +
                "this list yields no data. Use it to resolve the service a " +
                "human described in prose before calling any other tool.",
    )
    fun listServices(): ServiceCatalogResult =
        serviceCatalogQuery
            .run()
            .blockingGet(mcp.callTimeout, "listing known services")

    @Tool(
        description =
            "Return a compact health summary for a service over a recent " +
                "time window: request rate, error rate and latency p95/p99 " +
                "as descriptive statistics (min, max, mean, p95, trend, " +
                "delta). Provide 'lookback' as an ISO-8601 duration such as " +
                "PT30M or PT2H; it defaults to a bounded recent window. The " +
                "caller judges whether the values are anomalous.",
    )
    fun getServiceHealth(
        service: String,
        namespace: String,
        lookback: String?,
    ): ServiceHealthResult {
        Identifiers.require("service", service)
        Identifiers.require("namespace", namespace)
        val window = resolver.fromLookback(lookback)
        return serviceHealthQuery
            .run(service, namespace, window)
            .blockingGet(mcp.callTimeout, "computing health for $service")
    }

    @Tool(
        description =
            "Compare a service's metrics before and after a rollout to " +
                "judge whether a new version degraded it. Provide " +
                "'deployTime' as an ISO-8601 instant and 'beforeWindow' / " +
                "'afterWindow' as ISO-8601 durations such as PT30M. Returns " +
                "before/after error rate and latency p95/p99 with percentage " +
                "deltas, plus error fingerprints that appear only after the " +
                "deploy; the caller judges whether the change is a regression.",
    )
    fun compareServiceBeforeAfterRollout(
        service: String,
        namespace: String,
        deployTime: String,
        beforeWindow: String,
        afterWindow: String,
    ): RolloutComparisonResult {
        Identifiers.require("service", service)
        Identifiers.require("namespace", namespace)
        val request =
            RolloutQuery(
                service = service,
                namespace = namespace,
                deployTime = ToolInputs.instant("deployTime", deployTime),
                beforeWindow =
                    ToolInputs.positiveDuration(
                        "beforeWindow",
                        beforeWindow,
                    ),
                afterWindow =
                    ToolInputs.positiveDuration(
                        "afterWindow",
                        afterWindow,
                    ),
            )
        return rolloutComparisonQuery
            .run(request)
            .blockingGet(mcp.callTimeout, "comparing rollout for $service")
    }

    @Tool(
        description =
            "Measure the current error rate and latency p99 of a service's " +
                "upstream callers and downstream dependencies over a recent " +
                "window, to localise a bottleneck. Pass 'upstreamServices' " +
                "and 'downstreamServices' from the service profile and " +
                "'lookback' as an ISO-8601 duration such as PT1H. The caller " +
                "ranks which dependency is the likely cause.",
    )
    fun getDependencyImpact(
        service: String,
        namespace: String,
        upstreamServices: List<String>,
        downstreamServices: List<String>,
        lookback: String?,
    ): DependencyImpactResult {
        Identifiers.require("service", service)
        Identifiers.require("namespace", namespace)
        require(
            upstreamServices.size <= dependencyImpactQuery.maxDependencies,
        ) {
            "upstreamServices must contain at most " +
                "${dependencyImpactQuery.maxDependencies} items"
        }
        require(
            downstreamServices.size <= dependencyImpactQuery.maxDependencies,
        ) {
            "downstreamServices must contain at most " +
                "${dependencyImpactQuery.maxDependencies} items"
        }
        upstreamServices.forEach { Identifiers.require("upstreamService", it) }
        downstreamServices.forEach {
            Identifiers.require("downstreamService", it)
        }
        val window = resolver.fromLookback(lookback)
        val request =
            DependencyQuery(
                service = service,
                namespace = namespace,
                upstream = upstreamServices,
                downstream = downstreamServices,
            )
        return dependencyImpactQuery
            .run(request, window)
            .blockingGet(
                mcp.callTimeout,
                "computing dependency impact for $service",
            )
    }
}
