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

import com.opssage.admin.messaging.ConversationMessageResultEvent
import com.opssage.admin.messaging.ConversationMessageResultMetadata
import com.opssage.admin.messaging.InvestigationResultPayload
import com.opssage.admin.model.Confidence
import com.opssage.admin.model.InvestigationMessageRecord
import com.opssage.admin.model.InvestigationRequestStatus
import com.opssage.admin.model.UserRole
import com.opssage.admin.repository.InvestigationMessageRepository
import com.opssage.admin.security.UserPrincipal
import com.opssage.admin.service.InvestigationMessageStore
import com.opssage.admin.service.MessageCreateRequest
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@ExtendWith(MockKExtension::class)
class InvestigationMessageStoreTest {

    @MockK
    lateinit var repository: InvestigationMessageRepository

    private val saved = slot<InvestigationMessageRecord>()
    private val clock =
        Clock.fixed(Instant.parse("2026-07-13T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `creates accepted message record`() {
        every { repository.save(capture(saved)) } answers { firstArg() }

        store().create(createRequest())

        assertThat(saved.captured.command.metadata.messageId)
            .isEqualTo("msg-1")
        assertThat(saved.captured.command.metadata.requestId)
            .isEqualTo("req-1")
        assertThat(saved.captured.command.payload.conversationId)
            .isEqualTo("conv-1")
        assertThat(saved.captured.state.status)
            .isEqualTo(InvestigationRequestStatus.ACCEPTED)
    }

    @Test
    fun `complete stores message result`() {
        every { repository.findByMessageId("msg-1") } returns
            record()
        every { repository.save(capture(saved)) } answers { firstArg() }

        store().complete(resultEvent())

        val result = saved.captured.state.result
        assertThat(saved.captured.state.status)
            .isEqualTo(InvestigationRequestStatus.COMPLETED)
        assertThat(result?.conversationId).isEqualTo("conv-1")
        assertThat(result?.report?.confidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun `list owned clamps page size`() {
        every {
            repository.findByRequestIdAndUserId(
                "req-1",
                "user-1",
                any(),
            )
        } returns listOf(record())

        val records = store().listOwned("req-1", principal(), 500)

        assertThat(records).hasSize(1)
        verify {
            repository.findByRequestIdAndUserId(
                "req-1",
                "user-1",
                any(),
            )
        }
    }

    @Test
    fun `find owned delegates to repository`() {
        every {
            repository.findByMessageIdAndUserId(
                "msg-1",
                "user-1",
            )
        } returns record()

        val found = store().findOwned("msg-1", principal())

        assertThat(found).isNotNull
    }

    private fun store(): InvestigationMessageStore =
        InvestigationMessageStore(repository, clock)

    private fun createRequest(): MessageCreateRequest =
        MessageCreateRequest(
            messageId = "msg-1",
            principal = principal(),
            requestId = "req-1",
            conversationId = "conv-1",
            input = "show details",
        )

    private fun resultEvent(): ConversationMessageResultEvent =
        ConversationMessageResultEvent(
            metadata =
                ConversationMessageResultMetadata(
                    messageId = "msg-1",
                    requestId = "req-1",
                ),
            status = InvestigationRequestStatus.COMPLETED,
            result =
                InvestigationResultPayload(
                    conversationId = "conv-1",
                    summary = "details",
                    confidence = Confidence.HIGH,
                    evidence = listOf("metric"),
                ),
        )

    private fun record(): InvestigationMessageRecord {
        every { repository.save(any()) } answers { firstArg() }
        return store().create(createRequest())
    }

    private fun principal(): UserPrincipal =
        UserPrincipal(
            userId = "user-1",
            email = "sre@example.com",
            roles = setOf(UserRole.SRE),
        )
}
