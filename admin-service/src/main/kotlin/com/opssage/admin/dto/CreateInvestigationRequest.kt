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
package com.opssage.admin.dto

import com.opssage.admin.messaging.InvestigationCommand
import com.opssage.admin.messaging.InvestigationCommandMetadata
import com.opssage.admin.messaging.InvestigationCommandRequest
import com.opssage.admin.messaging.InvestigationCommandWindow
import com.opssage.admin.messaging.InvestigationRequester
import com.opssage.admin.model.InvestigationType
import com.opssage.admin.security.UserPrincipal
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

import java.time.Duration
import java.time.Instant

data class CreateInvestigationRequest(
    @field:Valid
    @field:NotNull
    val investigation: InvestigationInput?,
    @field:Valid
    val window: InvestigationWindowRequest? = null,
) {

    fun toCommand(
        requestId: String,
        principal: UserPrincipal,
    ): InvestigationCommand =
        InvestigationCommand(
            metadata =
                InvestigationCommandMetadata(
                    requestId = requestId,
                    requestedAt = Instant.now(),
                    requestedBy =
                        InvestigationRequester(
                            userId = principal.userId,
                            roles =
                                principal.roles.mapTo(mutableSetOf()) {
                                    it.name
                                },
                        ),
                ),
            request =
                InvestigationCommandRequest(
                    title = requireNotNull(investigation).title,
                    investigationType = investigation.investigationType,
                    input = investigation.input,
                ),
            window = window?.toCommandWindow(),
        )
}

data class InvestigationInput(
    @field:NotBlank
    val title: String,
    @field:NotNull
    val investigationType: InvestigationType,
    @field:NotBlank
    val input: String,
)

data class InvestigationWindowRequest(
    val from: Instant? = null,
    val to: Instant? = null,
    val lookback: Duration? = null,
) {

    fun toCommandWindow(): InvestigationCommandWindow =
        InvestigationCommandWindow(
            from = from,
            to = to,
            lookback = lookback,
        )
}
