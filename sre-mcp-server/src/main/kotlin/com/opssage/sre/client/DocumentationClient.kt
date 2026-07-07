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
package com.opssage.sre.client

import com.opssage.sre.config.DocumentationProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import reactor.core.publisher.Mono

import java.net.URI

import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

private val log = KotlinLogging.logger {}

@Component
class DocumentationClient(
    private val documentationWebClient: WebClient,
    private val documentation: DocumentationProperties,
) {

    fun fetch(link: String): Mono<String> =
        documentationWebClient
            .get()
            .uri(URI(link))
            .retrieve()
            .bodyToMono(String::class.java)
            .defaultIfEmpty("")
            .map { body -> body.take(documentation.maxDocumentChars) }
            .doOnError { error ->
                log.atWarn {
                    message = "Documentation fetch failed"
                    payload = mapOf("link" to link)
                    cause = error
                }
            }
}
