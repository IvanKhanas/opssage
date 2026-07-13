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

import com.opssage.sre.client.VictoriaMetricsClient
import com.opssage.sre.metrics.ServiceCatalogQuery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono

@ExtendWith(MockKExtension::class)
class ServiceCatalogQueryTest {

    @MockK
    private lateinit var client: VictoriaMetricsClient

    @Test
    fun `queries the configured service label and sorts the names`() {
        every { client.labelValues("service") } returns
            Mono.just(listOf("checkout-service", "cart"))

        val result = query().run().block()

        assertThat(result?.services).containsExactly("cart", "checkout-service")
        assertThat(result?.count).isEqualTo(2)
    }

    @Test
    fun `drops blank label values`() {
        every { client.labelValues(any()) } returns
            Mono.just(listOf("cart", "", "  "))

        assertThat(query().run().block()?.services).containsExactly("cart")
    }

    @Test
    fun `caps the catalog at the configured maximum`() {
        every { client.labelValues(any()) } returns
            Mono.just(listOf("a", "b", "c"))

        val result = query(maxServices = 2).run().block()

        assertThat(result?.services).containsExactly("a", "b")
        assertThat(result?.count).isEqualTo(2)
    }

    @Test
    fun `yields an empty catalog when the label has no values`() {
        every { client.labelValues(any()) } returns Mono.just(emptyList())

        assertThat(query().run().block()?.services).isEmpty()
    }

    private fun query(maxServices: Int = 200): ServiceCatalogQuery =
        ServiceCatalogQuery(
            client,
            MetricsFixtures.metricsProperties().copy(maxServices = maxServices),
        )
}
