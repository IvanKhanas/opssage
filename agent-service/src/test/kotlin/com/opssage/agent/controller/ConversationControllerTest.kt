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
package com.opssage.agent.controller

import com.opssage.agent.exception.ConversationNotFoundException
import com.opssage.agent.exception.GlobalExceptionHandler
import com.opssage.agent.investigation.InvestigationReport
import com.opssage.agent.investigation.InvestigationService
import com.opssage.agent.model.Confidence
import com.opssage.agent.model.Conversation
import com.opssage.agent.model.InvestigationType
import com.opssage.agent.repository.ConversationRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean

class ConversationControllerTest {

    private val conversationRepository = mockk<ConversationRepository>()
    private val investigationService = mockk<InvestigationService>()

    private val mockMvc =
        MockMvcBuilders
            .standaloneSetup(
                ConversationController(
                    conversationRepository,
                    investigationService,
                ),
            ).setControllerAdvice(GlobalExceptionHandler())
            .setValidator(
                LocalValidatorFactoryBean().apply { afterPropertiesSet() },
            ).build()

    @Test
    fun `returns 200 with the conversation when it exists`() {
        every { conversationRepository.findById("conv-1") } returns
            Conversation(
                id = "conv-1",
                title = "checkout alert",
                investigationType = InvestigationType.ALERT_INVESTIGATION,
            )

        mockMvc
            .get("/api/v1/conversations/conv-1")
            .andExpect {
                status { isOk() }
                jsonPath("$.id") { value("conv-1") }
                jsonPath("$.title") { value("checkout alert") }
            }
    }

    @Test
    fun `returns 404 when the conversation is missing`() {
        every { conversationRepository.findById("missing") } returns null

        mockMvc
            .get("/api/v1/conversations/missing")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `returns 200 with the list of conversations`() {
        every { conversationRepository.findAll() } returns
            listOf(
                Conversation(
                    id = "conv-1",
                    title = "checkout alert",
                    investigationType = InvestigationType.ALERT_INVESTIGATION,
                ),
            )

        mockMvc
            .get("/api/v1/conversations")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].id") { value("conv-1") }
            }
    }

    @Test
    fun `returns the report for a follow-up message`() {
        every { investigationService.reply("conv-1", "still down?") } returns
            InvestigationReport(
                conversationId = "conv-1",
                investigationType = InvestigationType.ALERT_INVESTIGATION,
                summary = "still degraded",
                confidence = Confidence.LOW,
                evidence = emptyList(),
            )

        mockMvc
            .post("/api/v1/conversations/conv-1/messages") {
                contentType = MediaType.APPLICATION_JSON
                content = """{ "input": "still down?" }"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.conversationId") { value("conv-1") }
                jsonPath("$.confidence") { value("LOW") }
            }
    }

    @Test
    fun `returns 400 when the follow-up message is blank`() {
        mockMvc
            .post("/api/v1/conversations/conv-1/messages") {
                contentType = MediaType.APPLICATION_JSON
                content = """{ "input": "" }"""
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `returns 404 when replying to a missing conversation`() {
        every { investigationService.reply("missing", "hello") } throws
            ConversationNotFoundException("missing")

        mockMvc
            .post("/api/v1/conversations/missing/messages") {
                contentType = MediaType.APPLICATION_JSON
                content = """{ "input": "hello" }"""
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.error") { value("conversation_not_found") }
            }
    }
}
