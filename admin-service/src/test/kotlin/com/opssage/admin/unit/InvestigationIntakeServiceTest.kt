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
import com.opssage.admin.messaging.InvestigationCommand
import com.opssage.admin.messaging.InvestigationCommandOutbox
import com.opssage.admin.model.InvestigationRequestRecord
import com.opssage.admin.model.InvestigationType
import com.opssage.admin.model.UserRole
import com.opssage.admin.security.UserPrincipal
import com.opssage.admin.service.InvestigationIntakeService
import com.opssage.admin.service.InvestigationRequestStore
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class InvestigationIntakeServiceTest {

    @MockK
    lateinit var outbox: InvestigationCommandOutbox

    @MockK
    lateinit var store: InvestigationRequestStore

    private val command = slot<InvestigationCommand>()

    @Test
    fun `submits command and returns generated request id`() {
        justRun { outbox.enqueue(capture(command)) }
        every { store.create(any(), any(), any()) } returns
            io.mockk.mockk<InvestigationRequestRecord>()
        val service = InvestigationIntakeService(store, outbox)

        val requestId = service.submit(request(), principal())

        assertThat(requestId).isNotBlank()
        assertThat(command.captured.metadata.requestId).isEqualTo(requestId)
        assertThat(command.captured.metadata.requestedBy.userId)
            .isEqualTo("user-1")
        assertThat(command.captured.request.title).isEqualTo("deposit alert")
        verify { store.create(requestId, any(), principal()) }
        verify { outbox.enqueue(any()) }
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
