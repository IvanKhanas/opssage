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

import com.opssage.agent.config.AgentMemoryProperties
import com.opssage.agent.config.ConfidenceProperties
import com.opssage.agent.config.WindowProperties
import com.opssage.agent.exception.ConversationNotFoundException
import com.opssage.agent.exception.InvestigationFailedException
import com.opssage.agent.investigation.AnchorWindowResolver
import com.opssage.agent.investigation.ConfidenceCalculator
import com.opssage.agent.investigation.InvestigationRequest
import com.opssage.agent.investigation.InvestigationService
import com.opssage.agent.llm.ConversationTurn
import com.opssage.agent.llm.LlmClient
import com.opssage.agent.llm.LlmInvocationException
import com.opssage.agent.llm.LlmVerdict
import com.opssage.agent.llm.TurnRole
import com.opssage.agent.model.Confidence
import com.opssage.agent.model.Conversation
import com.opssage.agent.model.ConversationStatus
import com.opssage.agent.model.InvestigationType
import com.opssage.agent.model.Message
import com.opssage.agent.model.MessageRole
import com.opssage.agent.repository.ConversationRepository
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

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@ExtendWith(MockKExtension::class)
class InvestigationServiceTest {

    @MockK
    lateinit var llmClient: LlmClient

    @MockK
    lateinit var conversationRepository: ConversationRepository

    private val confidenceCalculator =
        ConfidenceCalculator(
            ConfidenceProperties(
                mediumEvidenceThreshold = 1,
                highEvidenceThreshold = 3,
            ),
        )

    private val anchorWindowResolver =
        AnchorWindowResolver(
            WindowProperties(
                defaultLookback = Duration.ofHours(2),
                maxLookback = Duration.ofHours(48),
                forwardBuffer = Duration.ofMinutes(5),
            ),
            Clock.fixed(FIXED_NOW, ZoneOffset.UTC),
        )

    private lateinit var service: InvestigationService

    @BeforeEach
    fun setUp() {
        service =
            InvestigationService(
                llmClient,
                confidenceCalculator,
                conversationRepository,
                anchorWindowResolver,
                AgentMemoryProperties(maxMessages = 20),
            )
        every { conversationRepository.save(any()) } answers
            { (firstArg<Conversation>()).copy(id = "conv-1") }
    }

    @Test
    fun `sends the user prompt to the model unchanged`() {
        val sent = slot<String>()
        every { llmClient.investigate(any(), any(), capture(sent)) } returns
            LlmVerdict("ok", Confidence.LOW, emptyList())

        service.investigate(
            InvestigationRequest(
                title = "login errors",
                investigationType =
                    InvestigationType.USER_PROBLEM_INVESTIGATION,
                input = "why is checkout throwing 5xx after the deploy",
            ),
        )

        assertThat(sent.captured)
            .isEqualTo("why is checkout throwing 5xx after the deploy")
    }

    @Test
    fun `caps model confidence when evidence is missing`() {
        every { llmClient.investigate(any(), any(), any()) } returns
            LlmVerdict("root cause found", Confidence.HIGH, emptyList())

        val report =
            service.investigate(
                InvestigationRequest(
                    title = "alert",
                    investigationType =
                        InvestigationType.ALERT_INVESTIGATION,
                    input = "HighLatency fired",
                ),
            )

        assertThat(report.confidence).isEqualTo(Confidence.LOW)
        assertThat(report.conversationId).isEqualTo("conv-1")
    }

    @Test
    fun `persists a completed conversation with the user prompt`() {
        val saved = slot<Conversation>()
        every { conversationRepository.save(capture(saved)) } answers
            { saved.captured.copy(id = "conv-1") }
        every { llmClient.investigate(any(), any(), any()) } returns
            LlmVerdict(
                "checkout degraded",
                Confidence.HIGH,
                listOf("metric a", "log b", "trace c"),
            )

        val report =
            service.investigate(
                InvestigationRequest(
                    title = "rollout",
                    investigationType =
                        InvestigationType.ROLLOUT_HEALTH_CHECK,
                    input = "errors spiking after deploy",
                ),
            )

        val stored = saved.captured
        assertThat(stored.status).isEqualTo(ConversationStatus.COMPLETED)
        assertThat(stored.messages).hasSize(2)
        val userMessage = stored.messages.first { it.role == MessageRole.USER }
        assertThat(userMessage.content).isEqualTo("errors spiking after deploy")
        assertThat(report.confidence).isEqualTo(Confidence.HIGH)
        assertThat(report.evidence).hasSize(3)
        verify(exactly = 1) { conversationRepository.save(any()) }
    }

    @Test
    fun `persists a FAILED conversation and rethrows when the model fails`() {
        val saved = slot<Conversation>()
        every { conversationRepository.save(capture(saved)) } answers
            { saved.captured.copy(id = "conv-1") }
        every { llmClient.investigate(any(), any(), any()) } throws
            LlmInvocationException(
                "openai unavailable",
                RuntimeException("boom"),
            )

        assertThatThrownBy {
            service.investigate(
                InvestigationRequest(
                    title = "alert",
                    investigationType = InvestigationType.ALERT_INVESTIGATION,
                    input = "HighLatency fired",
                ),
            )
        }.isInstanceOf(InvestigationFailedException::class.java)

        assertThat(saved.captured.status).isEqualTo(ConversationStatus.FAILED)
        verify(exactly = 1) { conversationRepository.save(any()) }
    }

    @Test
    fun `persists the resolved default anchor window`() {
        val saved = slot<Conversation>()
        every { conversationRepository.save(capture(saved)) } answers
            { saved.captured.copy(id = "conv-1") }
        every { llmClient.investigate(any(), any(), any()) } returns
            LlmVerdict("ok", Confidence.LOW, emptyList())

        service.investigate(
            InvestigationRequest(
                title = "alert",
                investigationType = InvestigationType.ALERT_INVESTIGATION,
                input = "HighLatency fired",
            ),
        )

        val window = requireNotNull(saved.captured.anchorWindow)
        assertThat(window.to).isEqualTo(FIXED_NOW.plusSeconds(300))
        assertThat(window.from).isEqualTo(window.to.minusSeconds(2 * 60 * 60))
    }

    @Test
    fun `reply feeds prior turns as history and appends both messages`() {
        val stored =
            Conversation(
                id = "conv-1",
                title = "alert",
                investigationType = InvestigationType.ALERT_INVESTIGATION,
                status = ConversationStatus.COMPLETED,
                messages =
                    listOf(
                        message(MessageRole.USER, "HighLatency fired"),
                        message(MessageRole.ASSISTANT, "checkout is degraded"),
                    ),
            )
        every { conversationRepository.findById("conv-1") } returns stored
        val history = slot<List<ConversationTurn>>()
        val saved = slot<Conversation>()
        every {
            llmClient.investigate(any(), capture(history), any())
        } returns LlmVerdict("still degraded", Confidence.LOW, emptyList())
        every { conversationRepository.save(capture(saved)) } answers
            { saved.captured }

        val report = service.reply("conv-1", "is it still failing?")

        assertThat(history.captured).containsExactly(
            ConversationTurn(TurnRole.USER, "HighLatency fired"),
            ConversationTurn(TurnRole.ASSISTANT, "checkout is degraded"),
        )
        assertThat(saved.captured.messages).hasSize(4)
        assertThat(saved.captured.messages[2].content)
            .isEqualTo("is it still failing?")
        assertThat(saved.captured.status)
            .isEqualTo(ConversationStatus.COMPLETED)
        assertThat(report.conversationId).isEqualTo("conv-1")
    }

    @Test
    fun `reply caps the history at the configured maximum`() {
        val cappedService =
            InvestigationService(
                llmClient,
                confidenceCalculator,
                conversationRepository,
                anchorWindowResolver,
                AgentMemoryProperties(maxMessages = 1),
            )
        val stored =
            Conversation(
                id = "conv-1",
                title = "alert",
                investigationType = InvestigationType.ALERT_INVESTIGATION,
                messages =
                    listOf(
                        message(MessageRole.USER, "first"),
                        message(MessageRole.ASSISTANT, "second"),
                    ),
            )
        every { conversationRepository.findById("conv-1") } returns stored
        val history = slot<List<ConversationTurn>>()
        every {
            llmClient.investigate(any(), capture(history), any())
        } returns LlmVerdict("ok", Confidence.LOW, emptyList())
        every { conversationRepository.save(any()) } answers
            { firstArg() }

        cappedService.reply("conv-1", "next")

        assertThat(history.captured).containsExactly(
            ConversationTurn(TurnRole.ASSISTANT, "second"),
        )
    }

    @Test
    fun `reply throws when the conversation does not exist`() {
        every { conversationRepository.findById("missing") } returns null

        assertThatThrownBy { service.reply("missing", "hello") }
            .isInstanceOf(ConversationNotFoundException::class.java)
    }

    @Test
    fun `reply persists FAILED and keeps prior messages on model failure`() {
        val stored =
            Conversation(
                id = "conv-1",
                title = "alert",
                investigationType = InvestigationType.ALERT_INVESTIGATION,
                status = ConversationStatus.COMPLETED,
                messages =
                    listOf(
                        message(MessageRole.USER, "HighLatency fired"),
                    ),
            )
        every { conversationRepository.findById("conv-1") } returns stored
        val saved = slot<Conversation>()
        every { conversationRepository.save(capture(saved)) } answers
            { saved.captured }
        every { llmClient.investigate(any(), any(), any()) } throws
            LlmInvocationException(
                "openai unavailable",
                RuntimeException("boom"),
            )

        assertThatThrownBy { service.reply("conv-1", "still down?") }
            .isInstanceOf(InvestigationFailedException::class.java)

        assertThat(saved.captured.status).isEqualTo(ConversationStatus.FAILED)
        assertThat(saved.captured.messages).hasSize(2)
    }

    private fun message(
        role: MessageRole,
        content: String,
    ): Message =
        Message(
            id = UUID.randomUUID().toString(),
            role = role,
            content = content,
        )

    private companion object {
        val FIXED_NOW: Instant = Instant.parse("2026-07-08T10:00:00Z")
    }
}
