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
package com.opssage.agent.dto

import com.opssage.agent.investigation.InvestigationRequest
import com.opssage.agent.model.InvestigationType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

import java.time.Duration
import java.time.Instant

data class CreateInvestigationRequest(
    @field:NotBlank
    val title: String?,
    @field:NotNull
    val investigationType: InvestigationType?,
    @field:NotBlank
    val input: String?,
    val from: Instant? = null,
    val to: Instant? = null,
    val lookback: Duration? = null,
) {

    fun toInvestigationRequest(): InvestigationRequest =
        InvestigationRequest(
            title = requireNotNull(title),
            investigationType = requireNotNull(investigationType),
            input = requireNotNull(input),
            from = from,
            to = to,
            lookback = lookback,
        )
}
