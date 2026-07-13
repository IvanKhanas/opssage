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
package com.opssage.sre.mcp

import com.opssage.sre.config.DocumentationProperties
import com.opssage.sre.config.McpProperties
import com.opssage.sre.documentation.DocumentationService
import com.opssage.sre.dto.DocumentationResult
import com.opssage.sre.util.Urls
import com.opssage.sre.util.blockingGet

import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component

@Component
class DocumentationMcpTools(
    private val documentationService: DocumentationService,
    private val documentation: DocumentationProperties,
    private val mcp: McpProperties,
) : McpToolSet {

    @Tool(
        description =
            "Fetch the content of operator-provided documentation links and " +
                "return them as text for the model to read. Provide 'links' " +
                "as absolute http(s) URLs to markdown documents. Content is " +
                "capped per document and unreachable links are reported " +
                "separately. Use it when an operator attaches documentation " +
                "relevant to the incident under investigation.",
    )
    fun readDocumentation(links: List<String>): DocumentationResult {
        require(links.isNotEmpty()) { "links must not be empty" }
        require(links.size <= documentation.maxLinks) {
            "links must not exceed ${documentation.maxLinks} entries"
        }
        links.forEach { link -> Urls.requireHttpUrl("link", link) }
        return documentationService
            .read(links.distinct())
            .blockingGet(mcp.callTimeout, "reading documentation")
    }
}
