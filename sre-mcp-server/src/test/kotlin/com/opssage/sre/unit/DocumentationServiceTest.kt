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

import com.opssage.sre.client.DocumentationClient
import com.opssage.sre.documentation.DocumentationService
import com.opssage.sre.model.Confidence
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono

@ExtendWith(MockKExtension::class)
class DocumentationServiceTest {

    @MockK
    private lateinit var client: DocumentationClient

    private lateinit var service: DocumentationService

    @BeforeEach
    fun setUp() {
        service = DocumentationService(client)
    }

    @Test
    fun `loads pages and derives the title from the first heading`() {
        val link = "https://docs/deposit/runbook.md"
        every { client.fetch(link) } returns
            Mono.just("# Deposit rollback\n\nSteps to roll back.")

        val result = service.read(listOf(link)).block()!!

        assertThat(result.pages).hasSize(1)
        assertThat(result.pages[0].title).isEqualTo("Deposit rollback")
        assertThat(result.pages[0].sourceUrl).isEqualTo(link)
        assertThat(result.failedLinks).isEmpty()
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun `falls back to the file name when there is no heading`() {
        val link = "https://docs/deposit/overview.md"
        every { client.fetch(link) } returns Mono.just("no heading here")

        val result = service.read(listOf(link)).block()!!

        assertThat(result.pages[0].title).isEqualTo("overview.md")
    }

    @Test
    fun `reports failed and blank links with medium confidence`() {
        val ok = "https://docs/a.md"
        val blank = "https://docs/b.md"
        val broken = "https://docs/c.md"
        every { client.fetch(ok) } returns Mono.just("# A\ntext")
        every { client.fetch(blank) } returns Mono.just("   ")
        every { client.fetch(broken) } returns
            Mono.error(RuntimeException("boom"))

        val result = service.read(listOf(ok, blank, broken)).block()!!

        assertThat(result.pages).hasSize(1)
        assertThat(result.failedLinks).containsExactly(blank, broken)
        assertThat(result.confidence).isEqualTo(Confidence.MEDIUM)
    }

    @Test
    fun `reports low confidence when no link resolves`() {
        val link = "https://docs/gone.md"
        every { client.fetch(link) } returns
            Mono.error(RuntimeException("boom"))

        val result = service.read(listOf(link)).block()!!

        assertThat(result.pages).isEmpty()
        assertThat(result.failedLinks).containsExactly(link)
        assertThat(result.confidence).isEqualTo(Confidence.LOW)
    }
}
