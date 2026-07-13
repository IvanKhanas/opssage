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

import com.opssage.agent.exception.GlobalExceptionHandler
import com.opssage.agent.exception.InvestigationFailedException
import com.opssage.agent.investigation.InvestigationReport
import com.opssage.agent.investigation.InvestigationService
import com.opssage.agent.model.Confidence
import com.opssage.agent.model.InvestigationType
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean

class InvestigationControllerTest {

    private val investigationService = mockk<InvestigationService>()

    private val mockMvc =
        MockMvcBuilders
            .standaloneSetup(InvestigationController(investigationService))
            .setControllerAdvice(GlobalExceptionHandler())
            .setValidator(
                LocalValidatorFactoryBean().apply { afterPropertiesSet() },
            ).build()

    @Test
    fun `returns 201 with the report for a valid request`() {
        every { investigationService.investigate(any()) } returns
            InvestigationReport(
                conversationId = "conv-1",
                investigationType = InvestigationType.ALERT_INVESTIGATION,
                summary = "checkout degraded",
                confidence = Confidence.HIGH,
                evidence = listOf("metric a"),
            )

        mockMvc
            .post("/api/v1/investigations") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "title": "checkout alert",
                      "investigationType": "ALERT_INVESTIGATION",
                      "input": "HighLatency fired"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isCreated() }
                jsonPath("$.conversationId") { value("conv-1") }
                jsonPath("$.confidence") { value("HIGH") }
            }
    }

    @Test
    fun `returns 400 when the title is blank`() {
        mockMvc
            .post("/api/v1/investigations") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "title": "",
                      "investigationType": "ALERT_INVESTIGATION",
                      "input": "HighLatency fired"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `returns 502 when the investigation fails downstream`() {
        every { investigationService.investigate(any()) } throws
            InvestigationFailedException("model down", RuntimeException())

        mockMvc
            .post("/api/v1/investigations") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "title": "checkout alert",
                      "investigationType": "ALERT_INVESTIGATION",
                      "input": "HighLatency fired"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isBadGateway() }
                jsonPath("$.error") { value("investigation_failed") }
            }
    }
}
