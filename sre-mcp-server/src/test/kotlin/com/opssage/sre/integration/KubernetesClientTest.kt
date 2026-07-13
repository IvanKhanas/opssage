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
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.opssage.sre.client.KubernetesClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest
class KubernetesClientTest {

    @Autowired
    private lateinit var client: KubernetesClient

    @BeforeEach
    fun reset() {
        server.resetAll()
    }

    @Test
    fun `parses pod statuses and events`() {
        server.stubFor(
            get(urlPathEqualTo("/api/v1/namespaces/banking/pods"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(PODS_BODY),
                ),
        )
        server.stubFor(
            get(urlPathEqualTo("/api/v1/namespaces/banking/events"))
                .withQueryParam(
                    "fieldSelector",
                    equalTo("involvedObject.name=deposit-service-abc"),
                ).willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(EVENTS_BODY),
                ),
        )
        server.stubFor(
            get(urlPathEqualTo("/api/v1/namespaces/banking/events"))
                .withQueryParam(
                    "fieldSelector",
                    equalTo("involvedObject.name=deposit-service"),
                ).willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"items":[]}"""),
                ),
        )

        val state =
            client.serviceState("deposit-service", "banking").block()!!

        assertThat(state.pods).hasSize(1)
        val pod = state.pods[0]
        assertThat(pod.name).isEqualTo("deposit-service-abc")
        assertThat(pod.ready).isFalse()
        assertThat(pod.restartCount).isEqualTo(7)
        assertThat(pod.reason).isEqualTo("CrashLoopBackOff")
        assertThat(state.events).hasSize(1)
        assertThat(state.events[0].objectName)
            .isEqualTo("deposit-service-abc")
        assertThat(state.events[0].type).isEqualTo("Warning")
        server.verify(
            getRequestedFor(urlPathEqualTo("/api/v1/namespaces/banking/pods"))
                .withQueryParam("limit", equalTo("50")),
        )
        server.verify(
            getRequestedFor(urlPathEqualTo("/api/v1/namespaces/banking/events"))
                .withQueryParam(
                    "fieldSelector",
                    equalTo("involvedObject.name=deposit-service-abc"),
                ),
        )
    }

    @Test
    fun `falls back to the next app label when the first matches nothing`() {
        server.stubFor(
            get(urlPathEqualTo("/api/v1/namespaces/banking/pods"))
                .withQueryParam(
                    "labelSelector",
                    equalTo("app.kubernetes.io/name=deposit-service"),
                ).willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"items":[]}"""),
                ),
        )
        server.stubFor(
            get(urlPathEqualTo("/api/v1/namespaces/banking/pods"))
                .withQueryParam(
                    "labelSelector",
                    equalTo("app=deposit-service"),
                ).willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(PODS_BODY),
                ),
        )
        server.stubFor(
            get(urlPathEqualTo("/api/v1/namespaces/banking/events"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"items":[]}"""),
                ),
        )

        val state =
            client.serviceState("deposit-service", "banking").block()!!

        assertThat(state.pods).hasSize(1)
        server.verify(
            getRequestedFor(urlPathEqualTo("/api/v1/namespaces/banking/pods"))
                .withQueryParam(
                    "labelSelector",
                    equalTo("app.kubernetes.io/name=deposit-service"),
                ),
        )
        server.verify(
            getRequestedFor(urlPathEqualTo("/api/v1/namespaces/banking/pods"))
                .withQueryParam(
                    "labelSelector",
                    equalTo("app=deposit-service"),
                ),
        )
    }

    @Test
    fun `stops at the first app label that matches pods`() {
        server.stubFor(
            get(urlPathEqualTo("/api/v1/namespaces/banking/pods"))
                .withQueryParam(
                    "labelSelector",
                    equalTo("app.kubernetes.io/name=deposit-service"),
                ).willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(PODS_BODY),
                ),
        )
        server.stubFor(
            get(urlPathEqualTo("/api/v1/namespaces/banking/events"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"items":[]}"""),
                ),
        )

        client.serviceState("deposit-service", "banking").block()!!

        server.verify(
            0,
            getRequestedFor(urlPathEqualTo("/api/v1/namespaces/banking/pods"))
                .withQueryParam(
                    "labelSelector",
                    equalTo("app=deposit-service"),
                ),
        )
    }

    private companion object {
        private val server = WireMockServer(options().dynamicPort())

        val PODS_BODY =
            """
            {"items":[{"metadata":{"name":"deposit-service-abc"},"status":{"phase":"Running","containerStatuses":[{"ready":true,"restartCount":3,"state":{}},{"ready":false,"restartCount":4,"state":{"waiting":{"reason":"CrashLoopBackOff"}}}]}}]}
            """.trimIndent()

        val EVENTS_BODY =
            """
            {"items":[{"type":"Warning","reason":"BackOff","message":"Back-off restarting failed container","count":5,"lastTimestamp":"2026-07-06T10:00:00Z","involvedObject":{"kind":"Pod","name":"deposit-service-abc"}}]}
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
            registry.add("sre.kubernetes.base-url") { server.baseUrl() }
        }
    }
}
