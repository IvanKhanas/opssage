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
package com.opssage.sre.config

import io.netty.channel.ChannelOption
import io.netty.handler.ssl.SslContextBuilder
import reactor.netty.http.client.HttpClient

import java.io.File

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient

private data class WebClientSettings(
    val baseUrl: String,
    val caCertPath: String? = null,
)

@Configuration
class HttpClients {

    private companion object {
        private const val MAX_UTF8_BYTES_PER_CHAR = 4
    }

    @Bean
    fun victoriaMetricsWebClient(
        http: HttpClientProperties,
        metrics: MetricsProperties,
    ): WebClient = webClient(WebClientSettings(metrics.baseUrl), http)

    @Bean
    fun victoriaLogsWebClient(
        http: HttpClientProperties,
        logs: LogsProperties,
    ): WebClient = webClient(WebClientSettings(logs.baseUrl), http)

    @Bean
    fun victoriaTracesWebClient(
        http: HttpClientProperties,
        traces: TracesProperties,
    ): WebClient = webClient(WebClientSettings(traces.baseUrl), http)

    @Bean
    fun kubernetesWebClient(
        http: HttpClientProperties,
        kubernetes: KubernetesProperties,
        tokens: KubernetesTokens,
    ): WebClient =
        WebClient
            .builder()
            .baseUrl(kubernetes.baseUrl)
            .clientConnector(
                ReactorClientHttpConnector(
                    nettyClient(http, kubernetes.caCertPath),
                ),
            ).filter(bearerToken(tokens))
            .build()

    @Bean
    fun documentationWebClient(
        http: HttpClientProperties,
        documentation: DocumentationProperties,
    ): WebClient =
        WebClient
            .builder()
            .clientConnector(
                ReactorClientHttpConnector(nettyClient(http, null)),
            ).codecs { configurer ->
                configurer
                    .defaultCodecs()
                    .maxInMemorySize(
                        documentation.maxDocumentChars *
                            MAX_UTF8_BYTES_PER_CHAR,
                    )
            }.build()

    private fun bearerToken(tokens: KubernetesTokens): ExchangeFilterFunction =
        ExchangeFilterFunction { request, next ->
            val token = tokens.current()
            if (token.isBlank()) {
                next.exchange(request)
            } else {
                next.exchange(
                    ClientRequest
                        .from(request)
                        .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer $token",
                        ).build(),
                )
            }
        }

    private fun webClient(
        settings: WebClientSettings,
        http: HttpClientProperties,
    ): WebClient =
        WebClient
            .builder()
            .baseUrl(settings.baseUrl)
            .clientConnector(
                ReactorClientHttpConnector(
                    nettyClient(http, settings.caCertPath),
                ),
            ).build()

    private fun nettyClient(
        http: HttpClientProperties,
        caCertPath: String?,
    ): HttpClient {
        val client =
            HttpClient
                .create()
                .option(
                    ChannelOption.CONNECT_TIMEOUT_MILLIS,
                    http.connectTimeout.toMillis().toInt(),
                ).responseTimeout(http.responseTimeout)
        if (caCertPath.isNullOrBlank()) return client
        return client.secure { ssl ->
            ssl.sslContext(
                SslContextBuilder
                    .forClient()
                    .trustManager(File(caCertPath))
                    .build(),
            )
        }
    }
}
