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

import com.opssage.agent.config.LlmProperties
import com.opssage.agent.llm.ChatClientLlmClient
import com.opssage.agent.llm.ConversationTurn
import com.opssage.agent.llm.HistoryMasker
import com.opssage.agent.llm.LlmInvocationException
import com.opssage.agent.llm.LlmVerdictParser
import com.opssage.agent.llm.TurnRole
import com.opssage.agent.masking.MaskingResult
import com.opssage.agent.masking.PiiMasker
import com.opssage.agent.model.Confidence
import com.opssage.agent.model.Observation
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tools.jackson.module.kotlin.jacksonObjectMapper

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.web.client.RestClientException

@ExtendWith(MockKExtension::class)
class ChatClientLlmClientTest {

    @MockK
    lateinit var chatClient: ChatClient

    @MockK
    lateinit var toolFreeChatClient: ChatClient

    @MockK
    lateinit var masker: PiiMasker

    @MockK
    lateinit var reasoningSpec: ChatClient.ChatClientRequestSpec

    @MockK
    lateinit var reasoningCall: ChatClient.CallResponseSpec

    @MockK
    lateinit var finalizerSpec: ChatClient.ChatClientRequestSpec

    @MockK
    lateinit var finalizerCall: ChatClient.CallResponseSpec

    private val mapper = jacksonObjectMapper()

    private lateinit var client: ChatClientLlmClient

    @BeforeEach
    fun setUp() {
        every { masker.mask(any()) } answers
            { MaskingResult(firstArg(), masked = false) }

        every { chatClient.prompt() } returns reasoningSpec
        every { reasoningSpec.system(any<String>()) } returns reasoningSpec
        every {
            reasoningSpec.messages(any<List<Message>>())
        } returns reasoningSpec
        every { reasoningSpec.user(any<String>()) } returns reasoningSpec
        every { reasoningSpec.call() } returns reasoningCall
        every { reasoningCall.content() } returns "findings from tools"

        every { toolFreeChatClient.prompt() } returns finalizerSpec
        every { finalizerSpec.system(any<String>()) } returns finalizerSpec
        every { finalizerSpec.user(any<String>()) } returns finalizerSpec
        every {
            finalizerSpec.options(any<OpenAiChatOptions.Builder>())
        } returns finalizerSpec
        every { finalizerSpec.call() } returns finalizerCall

        client = clientWith(LlmProperties(4000, OBSERVATION_MAX_CHARS))
    }

    @Test
    fun `finalizes the reasoning phase into a structured verdict`() {
        every { finalizerCall.content() } returns VALID_JSON

        val verdict = investigate()

        assertThat(verdict.summary).isEqualTo("root cause")
        assertThat(verdict.confidence).isEqualTo(Confidence.HIGH)
        assertThat(verdict.evidence).containsExactly("log a", "metric b")
        verify(exactly = 1) { chatClient.prompt() }
        verify(exactly = 1) { toolFreeChatClient.prompt() }
    }

    @Test
    fun `repairs the output when finalization returns invalid JSON`() {
        every { finalizerCall.content() } returnsMany
            listOf("preamble without json", VALID_JSON)

        val verdict = investigate()

        assertThat(verdict.summary).isEqualTo("root cause")
        assertThat(verdict.confidence).isEqualTo(Confidence.HIGH)
        verify(exactly = 2) { toolFreeChatClient.prompt() }
    }

    @Test
    fun `normalizes useful json that does not exactly match the schema`() {
        every { finalizerCall.content() } returns LENIENT_JSON

        val verdict = investigate()

        assertThat(verdict.summary)
            .contains("service: deposit-service")
            .contains("conclusion: healthy")
        assertThat(verdict.confidence).isEqualTo(Confidence.HIGH)
        assertThat(verdict.evidence)
            .containsExactly("metrics: p99 is stable")
        verify(exactly = 1) { toolFreeChatClient.prompt() }
    }

    @Test
    fun `falls back to a low-confidence summary when repair also fails`() {
        every { finalizerCall.content() } returns "still not json"

        val verdict = investigate()

        assertThat(verdict.summary).isEqualTo("still not json")
        assertThat(verdict.confidence).isEqualTo(Confidence.LOW)
        assertThat(verdict.evidence).isEmpty()
        verify(exactly = 2) { toolFreeChatClient.prompt() }
    }

    @Test
    fun `masks the user input before the reasoning phase`() {
        val sent = slot<String>()
        every { reasoningSpec.user(capture(sent)) } returns reasoningSpec
        every { masker.mask(any()) } returns
            MaskingResult("MASKED", masked = true)
        every { finalizerCall.content() } returns VALID_JSON

        investigate(input = "mail me a@b.com")

        assertThat(sent.captured).isEqualTo("MASKED")
    }

    @Test
    fun `wraps transport failures into LlmInvocationException`() {
        every { reasoningCall.content() } throws RestClientException("down")

        assertThatThrownBy { investigate() }
            .isInstanceOf(LlmInvocationException::class.java)
    }

    @Test
    fun `replays prior turns as history in the reasoning phase`() {
        val history = slot<List<Message>>()
        every { reasoningSpec.messages(capture(history)) } returns reasoningSpec
        every { finalizerCall.content() } returns VALID_JSON

        client.investigate(
            "system",
            listOf(
                ConversationTurn(TurnRole.USER, "first"),
                ConversationTurn(TurnRole.ASSISTANT, "second"),
            ),
            "next",
            emptyList(),
        )

        assertThat(history.captured).hasSize(2)
    }

    @Test
    fun `appends the collected observations to the reasoning prompt`() {
        val sent = slot<String>()
        every { reasoningSpec.user(capture(sent)) } returns reasoningSpec
        every { finalizerCall.content() } returns VALID_JSON

        investigate(
            observations =
                listOf(
                    Observation("getServiceHealth", "p99 is 2s", true),
                    Observation("getServiceCorrectness", "нет данных", false),
                ),
        )

        assertThat(sent.captured)
            .contains("why 5xx")
            .contains("Собранные данные")
            .contains("### getServiceHealth")
            .contains("p99 is 2s")
            .contains("### getServiceCorrectness")
    }

    @Test
    fun `passes the collected observations to the finalizer`() {
        val sent = slot<String>()
        every { finalizerSpec.user(capture(sent)) } returns finalizerSpec
        every { finalizerCall.content() } returns VALID_JSON

        investigate(
            observations =
                listOf(
                    Observation("findTopLogErrors", "timeout count 42", true),
                ),
        )

        assertThat(sent.captured)
            .contains("Промежуточный вывод модели")
            .contains("findings from tools")
            .contains("### findTopLogErrors")
            .contains("timeout count 42")
    }

    @Test
    fun `sends no observation block when the playbook produced nothing`() {
        val sent = slot<String>()
        every { reasoningSpec.user(capture(sent)) } returns reasoningSpec
        every { finalizerCall.content() } returns VALID_JSON

        investigate()

        assertThat(sent.captured).isEqualTo("why 5xx")
    }

    @Test
    fun `truncates an oversized observation to the configured budget`() {
        val sent = slot<String>()
        every { reasoningSpec.user(capture(sent)) } returns reasoningSpec
        every { finalizerCall.content() } returns VALID_JSON
        client = clientWith(LlmProperties(4000, observationMaxChars = 4))

        investigate(
            observations =
                listOf(
                    Observation("getServiceHealth", "0123456789", true),
                ),
        )

        assertThat(sent.captured).contains("0123").doesNotContain("456789")
    }

    private fun clientWith(properties: LlmProperties): ChatClientLlmClient =
        ChatClientLlmClient(
            chatClient,
            toolFreeChatClient,
            masker,
            HistoryMasker(masker),
            LlmVerdictParser(mapper),
            properties,
        )

    private fun investigate(
        input: String = "why 5xx",
        observations: List<Observation> = emptyList(),
    ) = client.investigate("system", emptyList(), input, observations)

    private companion object {
        const val OBSERVATION_MAX_CHARS = 6000

        val VALID_JSON =
            """
            {
              "summary": "root cause",
              "confidence": "HIGH",
              "evidence": [
                "log a",
                "metric b"
              ]
            }
            """.trimIndent()

        const val LENIENT_JSON =
            """
            {
              "summary": {
                "service": "deposit-service",
                "conclusion": "healthy"
              },
              "confidence": "HIGH",
              "evidence": [
                {
                  "signal": "metrics",
                  "details": "p99 is stable"
                }
              ]
            }
            """
    }
}
