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

import com.opssage.sre.config.DocumentationProperties
import com.opssage.sre.config.McpProperties
import com.opssage.sre.documentation.DocumentationService
import com.opssage.sre.dto.DocumentationPage
import com.opssage.sre.dto.DocumentationResult
import com.opssage.sre.mcp.DocumentationMcpTools
import com.opssage.sre.model.Confidence
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono

import java.time.Duration

@ExtendWith(MockKExtension::class)
class DocumentationMcpToolsTest {

    @MockK
    private lateinit var service: DocumentationService

    private lateinit var tools: DocumentationMcpTools

    @BeforeEach
    fun setUp() {
        tools =
            DocumentationMcpTools(
                service,
                DocumentationProperties(maxLinks = 2, maxDocumentChars = 1000),
                McpProperties(Duration.ofSeconds(1)),
            )
    }

    @Test
    fun `rejects an empty link list`() {
        assertThatThrownBy {
            tools.readDocumentation(emptyList())
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("empty")
    }

    @Test
    fun `rejects more links than the configured cap`() {
        assertThatThrownBy {
            tools.readDocumentation(
                listOf(
                    "https://docs/a.md",
                    "https://docs/b.md",
                    "https://docs/c.md",
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("exceed")
    }

    @Test
    fun `rejects a link that is not an http url`() {
        assertThatThrownBy {
            tools.readDocumentation(listOf("ftp://docs/runbook.md"))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("link")
    }

    @Test
    fun `returns the resolved documentation result`() {
        val link = "https://docs/runbook.md"
        every { service.read(listOf(link)) } returns
            Mono.just(
                DocumentationResult(
                    pages =
                        listOf(DocumentationPage("Runbook", link, "body")),
                    failedLinks = emptyList(),
                    summary = "Loaded 1 of 1 documentation links.",
                    confidence = Confidence.HIGH,
                ),
            )

        val result = tools.readDocumentation(listOf(link))

        assertThat(result.pages).hasSize(1)
        assertThat(result.pages[0].title).isEqualTo("Runbook")
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
    }
}
