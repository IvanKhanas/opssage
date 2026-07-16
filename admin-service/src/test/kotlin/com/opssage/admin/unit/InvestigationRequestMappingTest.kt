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
import com.opssage.admin.dto.InvestigationWindowRequest
import com.opssage.admin.model.InvestigationType
import com.opssage.admin.model.UserRole
import com.opssage.admin.security.UserPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.time.Duration
import java.time.Instant

class InvestigationRequestMappingTest {

    @Test
    fun `maps api request to kafka command`() {
        val command =
            CreateInvestigationRequest(
                investigation =
                    InvestigationInput(
                        title = "deposit alert",
                        investigationType =
                            InvestigationType.ALERT_INVESTIGATION,
                        input = "HighErrorRate fired",
                    ),
                window =
                    InvestigationWindowRequest(
                        from = Instant.parse("2026-07-13T10:00:00Z"),
                        to = Instant.parse("2026-07-13T11:00:00Z"),
                        lookback = Duration.ofHours(4),
                    ),
            ).toCommand("req-1", principal())

        assertThat(command.metadata.requestId).isEqualTo("req-1")
        assertThat(command.metadata.requestedBy.userId).isEqualTo("user-1")
        assertThat(command.request.title).isEqualTo("deposit alert")
        assertThat(command.request.investigationType)
            .isEqualTo(InvestigationType.ALERT_INVESTIGATION)
        assertThat(command.request.input).isEqualTo("HighErrorRate fired")
        assertThat(command.window?.from)
            .isEqualTo(Instant.parse("2026-07-13T10:00:00Z"))
        assertThat(command.window?.to)
            .isEqualTo(Instant.parse("2026-07-13T11:00:00Z"))
        assertThat(command.window?.lookback).isEqualTo(Duration.ofHours(4))
    }

    private fun principal(): UserPrincipal =
        UserPrincipal(
            userId = "user-1",
            email = "sre@example.com",
            roles = setOf(UserRole.SRE),
        )
}
