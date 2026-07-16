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

import com.opssage.admin.dto.CreateInvestigationMessageRequest
import com.opssage.admin.messaging.ConversationMessageCommand
import com.opssage.admin.messaging.ConversationMessageOutbox
import com.opssage.admin.model.UserRole
import com.opssage.admin.security.UserPrincipal
import com.opssage.admin.service.ConversationMessageIntakeService
import com.opssage.admin.service.InvestigationMessageStore
import com.opssage.admin.service.InvestigationRequestStore
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

import org.springframework.web.server.ResponseStatusException

@ExtendWith(MockKExtension::class)
class ConversationMessageIntakeServiceTest {

    @MockK
    lateinit var requests: InvestigationRequestStore

    @MockK
    lateinit var messages: InvestigationMessageStore

    @MockK
    lateinit var outbox: ConversationMessageOutbox

    private val command = slot<ConversationMessageCommand>()

    @Test
    fun `submits follow up message for completed investigation`() {
        every { requests.findOwned("req-1", principal()) } returns
            InvestigationFixtures.completedRecord()
        every { messages.create(any()) } returns io.mockk.mockk()
        justRun { outbox.enqueue(capture(command)) }

        val messageId =
            service().submit(
                "req-1",
                CreateInvestigationMessageRequest("show details"),
                principal(),
            )

        assertThat(messageId).isNotBlank()
        assertThat(command.captured.metadata.requestId).isEqualTo("req-1")
        assertThat(command.captured.requester.userId).isEqualTo("user-1")
        assertThat(command.captured.body.conversation.conversationId)
            .isEqualTo("conv-1")
        assertThat(command.captured.body.payload.input)
            .isEqualTo("show details")
        verify { messages.create(any()) }
        verify { outbox.enqueue(any()) }
    }

    @Test
    fun `rejects message for missing investigation`() {
        every { requests.findOwned("req-1", principal()) } returns null

        assertThatThrownBy {
            service().submit(
                "req-1",
                CreateInvestigationMessageRequest("show details"),
                principal(),
            )
        }.isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `rejects message before conversation is ready`() {
        every { requests.findOwned("req-1", principal()) } returns
            InvestigationFixtures.acceptedRecord()

        assertThatThrownBy {
            service().submit(
                "req-1",
                CreateInvestigationMessageRequest("show details"),
                principal(),
            )
        }.isInstanceOf(ResponseStatusException::class.java)
    }

    private fun service(): ConversationMessageIntakeService =
        ConversationMessageIntakeService(requests, messages, outbox)

    private fun principal(): UserPrincipal =
        UserPrincipal(
            userId = "user-1",
            email = "sre@example.com",
            roles = setOf(UserRole.SRE),
        )
}
