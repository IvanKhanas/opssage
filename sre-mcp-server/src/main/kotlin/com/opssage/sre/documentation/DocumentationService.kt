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
package com.opssage.sre.documentation

import com.opssage.sre.client.DocumentationClient
import com.opssage.sre.dto.DocumentationPage
import com.opssage.sre.dto.DocumentationResult
import com.opssage.sre.util.ConfidenceCalculator
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import org.springframework.stereotype.Component

@Component
class DocumentationService(
    private val client: DocumentationClient,
) {

    fun read(links: List<String>): Mono<DocumentationResult> =
        Flux
            .fromIterable(links)
            .flatMapSequential(
                { link -> fetchOutcome(link) },
                links.size.coerceAtLeast(1),
            ).collectList()
            .map { outcomes -> toResult(links.size, outcomes) }

    private fun fetchOutcome(link: String): Mono<PageOutcome> =
        client
            .fetch(link)
            .map { content ->
                if (content.isBlank()) {
                    FailedPageOutcome(link)
                } else {
                    LoadedPageOutcome(
                        DocumentationPage(
                            DocumentationTitle.of(content, link),
                            link,
                            content,
                        ),
                    )
                }
            }.onErrorReturn(FailedPageOutcome(link))

    private fun toResult(
        requested: Int,
        outcomes: List<PageOutcome>,
    ): DocumentationResult {
        val pages =
            outcomes.filterIsInstance<LoadedPageOutcome>().map { it.page }
        val failed =
            outcomes.filterIsInstance<FailedPageOutcome>().map { it.link }
        return DocumentationResult(
            pages = pages,
            failedLinks = failed,
            summary =
                "Loaded ${pages.size} of $requested documentation links.",
            confidence = ConfidenceCalculator.of(pages.size, requested),
        )
    }
}
