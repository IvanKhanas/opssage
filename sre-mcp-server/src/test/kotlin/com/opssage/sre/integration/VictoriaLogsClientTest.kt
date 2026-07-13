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
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.opssage.sre.client.VictoriaLogsClient
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
class VictoriaLogsClientTest {

    @Autowired
    private lateinit var client: VictoriaLogsClient

    @BeforeEach
    fun reset() {
        server.resetAll()
    }

    @Test
    fun `parses ndjson log lines into records`() {
        server.stubFor(
            get(urlPathEqualTo("/select/logsql/query"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/x-ndjson")
                        .withBody(NDJSON_BODY),
                ),
        )
        val window =
            TimeWindow(
                Instant.parse("2026-06-27T10:00:00Z"),
                Instant.parse("2026-06-27T11:00:00Z"),
            )

        val records =
            client.errorLogs("deposit-service", "banking", window).block()!!

        assertThat(records).hasSize(2)
        assertThat(records[0].message)
            .isEqualTo("Timeout while calling core-banking-adapter")
        assertThat(records[0].traceId).isEqualTo("abc-123")
        assertThat(records[1].traceId).isEqualTo("abc-456")
    }

    @Test
    fun `returns empty list when query body is empty`() {
        server.stubFor(
            get(urlPathEqualTo("/select/logsql/query"))
                .willReturn(aResponse().withStatus(200)),
        )
        val window =
            TimeWindow(
                Instant.parse("2026-06-27T10:00:00Z"),
                Instant.parse("2026-06-27T11:00:00Z"),
            )

        val records =
            client.errorLogs("deposit-service", "banking", window).block()!!

        assertThat(records).isEmpty()
    }

    @Test
    fun `matches every configured error level in one logsql filter`() {
        server.stubFor(
            get(urlPathEqualTo("/select/logsql/query"))
                .willReturn(aResponse().withStatus(200)),
        )
        val window =
            TimeWindow(
                Instant.parse("2026-06-27T10:00:00Z"),
                Instant.parse("2026-06-27T11:00:00Z"),
            )

        client.errorLogs("deposit-service", "banking", window).block()

        server.verify(
            getRequestedFor(urlPathEqualTo("/select/logsql/query"))
                .withQueryParam(
                    "query",
                    containing("""(level:="ERROR" OR level:="error")"""),
                ),
        )
    }

    private companion object {
        private val server = WireMockServer(options().dynamicPort())

        val NDJSON_BODY =
            """
            {"_time":"2026-06-27T10:32:11Z","_msg":"Timeout while calling core-banking-adapter","level":"ERROR","trace_id":"abc-123"}
            {"_time":"2026-06-27T10:33:00Z","_msg":"Timeout while calling core-banking-adapter","level":"ERROR","trace_id":"abc-456"}
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
            registry.add("sre.victoria-logs.base-url") { server.baseUrl() }
            registry.add("sre.victoria-logs.error-levels") { "ERROR,error" }
        }
    }
}
