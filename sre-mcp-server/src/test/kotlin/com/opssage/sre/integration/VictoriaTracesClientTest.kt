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
import com.opssage.sre.client.VictoriaTracesClient
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
class VictoriaTracesClientTest {

    @Autowired
    private lateinit var client: VictoriaTracesClient

    @BeforeEach
    fun reset() {
        server.resetAll()
    }

    @Test
    fun `parses jaeger traces with resolved services and errors`() {
        server.stubFor(
            get(urlPathEqualTo("/select/jaeger/api/traces"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(TRACES_BODY),
                ),
        )
        val window =
            TimeWindow(
                Instant.parse("2026-06-27T10:00:00Z"),
                Instant.parse("2026-06-27T11:00:00Z"),
            )

        val traces =
            client
                .findTraces("deposit-service", "banking", "u1", window, 20)
                .block()!!

        assertThat(traces).hasSize(1)
        val spans = traces[0].spans
        assertThat(spans).hasSize(2)
        val child = spans.first { it.spanId == "s2" }
        assertThat(child.parentSpanId).isEqualTo("s1")
        assertThat(child.serviceName).isEqualTo("deposit-service")
        assertThat(child.error).isTrue()
        assertThat(spans.first { it.spanId == "s1" }.error).isFalse()
    }

    @Test
    fun `finds service traces without a user tag`() {
        server.stubFor(
            get(urlPathEqualTo("/select/jaeger/api/traces"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(TRACES_BODY),
                ),
        )
        val window =
            TimeWindow(
                Instant.parse("2026-06-27T10:00:00Z"),
                Instant.parse("2026-06-27T11:00:00Z"),
            )

        val traces =
            client
                .findServiceTraces("deposit-service", "banking", window, 20)
                .block()!!

        assertThat(traces).hasSize(1)
        assertThat(traces[0].spans).hasSize(2)
    }

    private companion object {
        private val server = WireMockServer(options().dynamicPort())

        val TRACES_BODY =
            """
            {"data":[{"traceID":"t1","spans":[{"spanID":"s1","operationName":"GET /pay","references":[],"startTime":1000000,"duration":500000,"tags":[{"key":"error","value":"unset"}],"processID":"p1"},{"spanID":"s2","operationName":"charge","references":[{"refType":"CHILD_OF","spanID":"s1"}],"startTime":1100000,"duration":300000,"tags":[{"key":"error","value":true}],"processID":"p2"}],"processes":{"p1":{"serviceName":"gateway"},"p2":{"serviceName":"deposit-service"}}}]}
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
            registry.add("sre.victoria-traces.base-url") { server.baseUrl() }
        }
    }
}
