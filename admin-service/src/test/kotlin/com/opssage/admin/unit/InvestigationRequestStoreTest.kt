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

import com.opssage.admin.dto.CreateInvestigationRequest
import com.opssage.admin.dto.InvestigationInput
import com.opssage.admin.messaging.InvestigationResultEvent
import com.opssage.admin.messaging.InvestigationResultPayload
import com.opssage.admin.model.Confidence
import com.opssage.admin.model.InvestigationRequestRecord
import com.opssage.admin.model.InvestigationRequestStatus
import com.opssage.admin.model.InvestigationType
import com.opssage.admin.model.UserRole
import com.opssage.admin.repository.InvestigationRequestRepository
import com.opssage.admin.security.UserPrincipal
import com.opssage.admin.service.InvestigationRequestStore
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
class InvestigationRequestStoreTest {

    @MockK
    lateinit var repository: InvestigationRequestRepository

    private val saved = slot<InvestigationRequestRecord>()
    private val clock =
        Clock.fixed(Instant.parse("2026-07-13T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `creates accepted record for current user`() {
        every { repository.save(capture(saved)) } answers { firstArg() }

        store().create("req-1", request(), principal())

        assertThat(saved.captured.command.requestId).isEqualTo("req-1")
        assertThat(saved.captured.command.requester.userId).isEqualTo("user-1")
        assertThat(saved.captured.state.status)
            .isEqualTo(InvestigationRequestStatus.ACCEPTED)
    }

    @Test
    fun `complete stores investigation result`() {
        every { repository.findByCommandRequestId("req-1") } returns record()
        every { repository.save(capture(saved)) } answers { firstArg() }

        store().complete(resultEvent())

        val result = saved.captured.state.result
        assertThat(saved.captured.state.status)
            .isEqualTo(InvestigationRequestStatus.COMPLETED)
        assertThat(result?.conversationId).isEqualTo("conv-1")
        assertThat(result?.report?.confidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun `find owned returns only current user's record`() {
        every { repository.findByCommandRequestId("req-1") } returns record()

        val found = store().findOwned("req-1", principal())

        assertThat(found).isNotNull
    }

    @Test
    fun `list owned clamps page size`() {
        every { repository.findByCommandRequesterUserId(eq("user-1"), any()) }
            .returns(listOf(record()))

        val records = store().listOwned(principal(), 500)

        assertThat(records).hasSize(1)
        verify { repository.findByCommandRequesterUserId("user-1", any()) }
    }

    private fun store(): InvestigationRequestStore =
        InvestigationRequestStore(repository, clock)

    private fun resultEvent(): InvestigationResultEvent =
        InvestigationResultEvent(
            requestId = "req-1",
            status = InvestigationRequestStatus.COMPLETED,
            result =
                InvestigationResultPayload(
                    conversationId = "conv-1",
                    summary = "deposit degraded",
                    confidence = Confidence.HIGH,
                    evidence = listOf("metric"),
                ),
        )

    private fun record(): InvestigationRequestRecord {
        every { repository.save(any()) } answers { firstArg() }
        return store().create("req-1", request(), principal())
    }

    private fun request(): CreateInvestigationRequest =
        CreateInvestigationRequest(
            investigation =
                InvestigationInput(
                    title = "deposit alert",
                    investigationType = InvestigationType.ALERT_INVESTIGATION,
                    input = "HighErrorRate fired",
                ),
        )

    private fun principal(): UserPrincipal =
        UserPrincipal(
            userId = "user-1",
            email = "sre@example.com",
            roles = setOf(UserRole.SRE),
        )
}
