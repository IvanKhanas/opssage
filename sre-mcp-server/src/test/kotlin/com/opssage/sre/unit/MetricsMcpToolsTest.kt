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

import com.opssage.sre.config.McpProperties
import com.opssage.sre.mcp.MetricsMcpTools
import com.opssage.sre.metrics.DependencyImpactQuery
import com.opssage.sre.metrics.RolloutComparisonQuery
import com.opssage.sre.metrics.ServiceCatalogQuery
import com.opssage.sre.metrics.ServiceHealthQuery
import com.opssage.sre.time.TimeWindowResolver
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

import java.time.Duration

@ExtendWith(MockKExtension::class)
class MetricsMcpToolsTest {

    @MockK
    private lateinit var serviceHealthQuery: ServiceHealthQuery

    @MockK
    private lateinit var rolloutComparisonQuery: RolloutComparisonQuery

    @MockK
    private lateinit var dependencyImpactQuery: DependencyImpactQuery

    @MockK
    private lateinit var serviceCatalogQuery: ServiceCatalogQuery

    @MockK
    private lateinit var resolver: TimeWindowResolver

    private lateinit var tools: MetricsMcpTools

    @BeforeEach
    fun setUp() {
        tools =
            MetricsMcpTools(
                serviceHealthQuery,
                rolloutComparisonQuery,
                dependencyImpactQuery,
                serviceCatalogQuery,
                resolver,
                McpProperties(Duration.ofSeconds(1)),
            )
    }

    @Test
    fun `rejects dependency lists above the configured bound`() {
        every { dependencyImpactQuery.maxDependencies } returns 1

        assertThatThrownBy {
            tools.getDependencyImpact(
                service = "deposit-service",
                namespace = "banking",
                upstreamServices = listOf("mobile-api", "web-api"),
                downstreamServices = emptyList(),
                lookback = "PT1H",
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("upstreamServices")
    }

    @Test
    fun `rejects non-positive rollout windows`() {
        assertThatThrownBy {
            tools.compareServiceBeforeAfterRollout(
                service = "deposit-service",
                namespace = "banking",
                deployTime = "2026-06-27T10:30:00Z",
                beforeWindow = "PT0S",
                afterWindow = "PT30M",
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("positive duration")
    }
}
