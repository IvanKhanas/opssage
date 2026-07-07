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
package com.opssage.sre.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.opssage.sre.client.VictoriaMetricsClient
import com.opssage.sre.time.TimeWindow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import java.time.Instant

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest
class VictoriaMetricsClientTest {

    @Autowired
    private lateinit var client: VictoriaMetricsClient

    @BeforeEach
    fun reset() {
        server.resetAll()
    }

    @Test
    fun `parses a prometheus matrix response into metric series`() {
        server.stubFor(
            get(urlPathEqualTo("/api/v1/query_range"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(MATRIX_BODY),
                ),
        )
        val window =
            TimeWindow(
                Instant.parse("2026-06-27T10:00:00Z"),
                Instant.parse("2026-06-27T11:00:00Z"),
            )

        val series = client.queryRange("up", window, 60).block()!!

        assertThat(series).hasSize(1)
        assertThat(series[0].name)
            .isEqualTo("http_server_requests_seconds_count")
        assertThat(series[0].labels).containsEntry("service", "deposit-service")
        assertThat(series[0].points).hasSize(2)
        assertThat(series[0].points[0].value).isEqualTo(1.0)
    }

    private companion object {
        private val server = WireMockServer(options().dynamicPort())

        val MATRIX_BODY =
            """
            {"status":"success","data":{"resultType":"matrix","result":[
              {"metric":{"__name__":"http_server_requests_seconds_count",
                "service":"deposit-service"},
               "values":[[1782900000,"1"],[1782900060,"2"]]}]}}
            """.trimIndent()

        @JvmStatic
        @BeforeAll
        fun start() {
            server.start()
        }

        @JvmStatic
        @AfterAll
        fun stop() {
            server.stop()
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("sre.victoria-metrics.base-url") { server.baseUrl() }
        }
    }
}
