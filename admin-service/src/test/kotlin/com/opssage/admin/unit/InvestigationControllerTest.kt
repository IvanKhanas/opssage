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
package com.opssage.admin.unit

import com.opssage.admin.controller.InvestigationController
import com.opssage.admin.controller.InvestigationEndpointServices
import com.opssage.admin.controller.InvestigationMessageEndpointServices
import com.opssage.admin.model.UserRole
import com.opssage.admin.security.CurrentUser
import com.opssage.admin.security.UserPrincipal
import com.opssage.admin.service.ConversationMessageIntakeService
import com.opssage.admin.service.InvestigationIntakeService
import com.opssage.admin.service.InvestigationMessageStore
import com.opssage.admin.service.InvestigationRequestStore
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean

@ExtendWith(MockKExtension::class)
class InvestigationControllerTest {

    @MockK
    lateinit var intakeService: InvestigationIntakeService

    @MockK
    lateinit var currentUser: CurrentUser

    @MockK
    lateinit var messageIntakeService: ConversationMessageIntakeService

    @MockK
    lateinit var store: InvestigationRequestStore

    @MockK
    lateinit var messageStore: InvestigationMessageStore

    private val mockMvc by lazy {
        val controller =
            InvestigationController(
                InvestigationEndpointServices(
                    intakeService,
                    store,
                    InvestigationMessageEndpointServices(
                        messageIntakeService,
                        messageStore,
                    ),
                ),
                currentUser,
            )
        MockMvcBuilders
            .standaloneSetup(controller)
            .setValidator(
                LocalValidatorFactoryBean().apply { afterPropertiesSet() },
            ).build()
    }

    @Test
    fun `returns accepted request id for a valid investigation`() {
        every { currentUser.requirePrincipal() } returns principal()
        every { intakeService.submit(any(), principal()) } returns "req-1"

        mockMvc
            .post("/api/v1/investigations") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "investigation": {
                        "title": "deposit alert",
                        "investigationType": "ALERT_INVESTIGATION",
                        "input": "HighErrorRate fired"
                      }
                    }
                    """.trimIndent()
            }.andExpect {
                status { isAccepted() }
                jsonPath("$.requestId") { value("req-1") }
                jsonPath("$.status") { value("ACCEPTED") }
            }
    }

    @Test
    fun `returns bad request when investigation is missing`() {
        mockMvc
            .post("/api/v1/investigations") {
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect {
                status { isBadRequest() }
            }
    }

    private fun principal(): UserPrincipal =
        UserPrincipal(
            userId = "user-1",
            email = "sre@example.com",
            roles = setOf(UserRole.SRE),
        )
}
