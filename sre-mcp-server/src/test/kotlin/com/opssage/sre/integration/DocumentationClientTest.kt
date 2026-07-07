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
import com.opssage.sre.client.DocumentationClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    properties = ["sre.documentation.max-document-chars=256"],
)
class DocumentationClientTest {

    @Autowired
    private lateinit var client: DocumentationClient

    @BeforeEach
    fun reset() {
        server.resetAll()
    }

    @Test
    fun `fetches markdown content over http`() {
        server.stubFor(
            get(urlPathEqualTo("/runbook.md"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "text/markdown")
                        .withBody("# Runbook\n\nRollback steps."),
                ),
        )

        val body =
            client.fetch("${server.baseUrl()}/runbook.md").block()!!

        assertThat(body).contains("# Runbook")
        assertThat(body).contains("Rollback steps.")
    }

    @Test
    fun `caps document content at the configured limit`() {
        server.stubFor(
            get(urlPathEqualTo("/big.md"))
                .willReturn(aResponse().withBody("a".repeat(500))),
        )

        val body = client.fetch("${server.baseUrl()}/big.md").block()!!

        assertThat(body).hasSize(256)
    }

    private companion object {
        private val server = WireMockServer(options().dynamicPort())

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
    }
}
