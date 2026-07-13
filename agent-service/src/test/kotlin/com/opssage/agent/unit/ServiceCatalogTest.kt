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
import com.opssage.agent.masking.MaskedToolRegistry
import com.opssage.agent.tools.ToolOutputReader
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tools.jackson.module.kotlin.jacksonObjectMapper

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

@ExtendWith(MockKExtension::class)
class ServiceCatalogTest {

    @MockK
    lateinit var registry: MaskedToolRegistry

    private var now: Instant = START

    private val clock =
        object : Clock() {
            override fun instant(): Instant = now

            override fun getZone(): ZoneOffset = ZoneOffset.UTC

            override fun withZone(zone: java.time.ZoneId): Clock = this
        }

    private lateinit var catalog: ServiceCatalog

    @BeforeEach
    fun setUp() {
        now = START
        every { registry.contains("listServices") } returns true
        catalog =
            ServiceCatalog(
                registry,
                ToolOutputReader(jacksonObjectMapper()),
                SreProperties("prod", Duration.ofMinutes(5)),
                clock,
            )
    }

    @Test
    fun `reads the service names the telemetry actually reports`() {
        every { registry.call("listServices", "{}") } returns CATALOG_JSON

        assertThat(catalog.services())
            .containsExactly("cart", "checkout-service")
    }

    @Test
    fun `reads the service names from mcp content arrays`() {
        every { registry.call("listServices", "{}") } returns mcpContentJson()

        assertThat(catalog.services())
            .containsExactly("cart", "checkout-service")
    }

    @Test
    fun `serves the cached catalog until the ttl expires`() {
        every { registry.call("listServices", "{}") } returns CATALOG_JSON

        catalog.services()
        now = START.plus(Duration.ofMinutes(4))
        catalog.services()

        verify(exactly = 1) { registry.call("listServices", "{}") }
    }

    @Test
    fun `reloads the catalog once the ttl has expired`() {
        every { registry.call("listServices", "{}") } returns CATALOG_JSON

        catalog.services()
        now = START.plus(Duration.ofMinutes(6))
        catalog.services()

        verify(exactly = 2) { registry.call("listServices", "{}") }
    }

    @Test
    fun `yields an empty catalog when the tool is not exposed`() {
        every { registry.contains("listServices") } returns false

        assertThat(catalog.services()).isEmpty()
        verify(exactly = 0) { registry.call(any(), any()) }
    }

    @Test
    fun `yields an empty catalog when the tool call fails`() {
        every { registry.call("listServices", "{}") } throws
            IllegalStateException("victoria down")

        assertThat(catalog.services()).isEmpty()
    }

    @Test
    fun `does not cache a failure so the next call retries`() {
        every { registry.call("listServices", "{}") } throws
            IllegalStateException("victoria down")

        catalog.services()
        catalog.services()

        verify(exactly = 2) { registry.call("listServices", "{}") }
    }

    private fun mcpContentJson(): String =
        jacksonObjectMapper().writeValueAsString(
            listOf(
                mapOf(
                    "type" to "text",
                    "text" to CATALOG_JSON,
                ),
            ),
        )

    private companion object {
        val START: Instant = Instant.parse("2026-07-09T10:00:00Z")

        const val CATALOG_JSON =
            """{"services":["cart","checkout-service"],"count":2}"""
    }
}
