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
package com.opssage.agent.unit

import com.opssage.agent.catalog.ServiceCatalog
import com.opssage.agent.config.SreProperties
import com.opssage.agent.llm.ChatClientTargetPlanner
import com.opssage.agent.llm.HistoryMasker
import com.opssage.agent.llm.LlmInvocationException
import com.opssage.agent.masking.MaskingResult
import com.opssage.agent.masking.PiiMasker
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tools.jackson.module.kotlin.jacksonObjectMapper

import java.time.Duration

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.web.client.RestClientException

@ExtendWith(MockKExtension::class)
class ChatClientTargetPlannerTest {

    @MockK
    lateinit var chatClient: ChatClient

    @MockK
    lateinit var masker: PiiMasker

    @MockK
    lateinit var catalog: ServiceCatalog

    @MockK
    lateinit var spec: ChatClient.ChatClientRequestSpec

    @MockK
    lateinit var call: ChatClient.CallResponseSpec

    private lateinit var planner: ChatClientTargetPlanner

    @BeforeEach
    fun setUp() {
        every { masker.mask(any()) } answers
            { MaskingResult(firstArg(), masked = false) }
        every { chatClient.prompt() } returns spec
        every { spec.system(any<String>()) } returns spec
        every { spec.messages(any<List<Message>>()) } returns spec
        every { spec.user(any<String>()) } returns spec
        every { spec.options(any<OpenAiChatOptions.Builder>()) } returns spec
        every { spec.call() } returns call
        every { catalog.services() } returns emptyList()

        planner =
            ChatClientTargetPlanner(
                chatClient,
                jacksonObjectMapper(),
                masker,
                HistoryMasker(masker),
                catalog,
                SreProperties(
                    namespace = "prod",
                    catalogTtl = Duration.ofMinutes(5),
                ),
            )
    }

    @Test
    fun `takes the namespace from configuration not from the model`() {
        every { call.content() } returns """{"service":"checkout"}"""

        val target = planner.plan(emptyList(), "checkout throws 5xx")

        assertThat(target?.service).isEqualTo("checkout")
        assertThat(target?.namespace).isEqualTo("prod")
    }

    @Test
    fun `does not invent catalog service names from human aliases`() {
        every { catalog.services() } returns listOf("payment-service")
        every { call.content() } returns """{"service":"payments"}"""

        assertThat(planner.plan(emptyList(), "payments are slow")).isNull()
    }

    @Test
    fun `rejects planned services that are not in the catalog`() {
        every { catalog.services() } returns listOf("payment-service")
        every { call.content() } returns """{"service":"billing-service"}"""

        assertThat(planner.plan(emptyList(), "billing is broken")).isNull()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """{"service":""}""",
            """{"service":"   "}""",
            "no json at all",
            """{"service":"оплата"}""",
            """{"service":"checkout service"}""",
            """{"service":"drop table;"}""",
        ],
    )
    fun `yields no target when the service is absent or unusable`(
        content: String,
    ) {
        every { call.content() } returns content

        assertThat(planner.plan(emptyList(), "что-то сломалось")).isNull()
    }

    @Test
    fun `masks the user input before asking the model for a service`() {
        val sent = slot<String>()
        every { spec.user(capture(sent)) } returns spec
        every { masker.mask(any()) } returns
            MaskingResult("MASKED", masked = true)
        every { call.content() } returns """{"service":"checkout"}"""

        planner.plan(emptyList(), "жалоба от a@b.com")

        assertThat(sent.captured).endsWith("MASKED")
        assertThat(sent.captured).doesNotContain("a@b.com")
    }

    @Test
    fun `wraps transport failures into LlmInvocationException`() {
        every { call.content() } throws RestClientException("down")

        assertThatThrownBy { planner.plan(emptyList(), "checkout 5xx") }
            .isInstanceOf(LlmInvocationException::class.java)
    }
}
