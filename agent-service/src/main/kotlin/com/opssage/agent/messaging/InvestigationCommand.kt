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
package com.opssage.agent.messaging

import com.opssage.agent.investigation.InvestigationRequest
import com.opssage.agent.model.InvestigationType

import java.time.Duration
import java.time.Instant

data class InvestigationCommand(
    val metadata: InvestigationCommandMetadata,
    val request: InvestigationCommandRequest,
    val window: InvestigationCommandWindow? = null,
) {

    fun toInvestigationRequest(): InvestigationRequest =
        InvestigationRequest(
            title = request.title,
            investigationType = request.investigationType,
            input = request.input,
            from = window?.from,
            to = window?.to,
            lookback = window?.lookback,
        )
}

data class InvestigationCommandMetadata(
    val requestId: String,
    val requestedAt: Instant,
    val requestedBy: InvestigationRequester? = null,
)

data class InvestigationRequester(
    val userId: String,
    val roles: Set<String>,
)

data class InvestigationCommandRequest(
    val title: String,
    val investigationType: InvestigationType,
    val input: String,
)

data class InvestigationCommandWindow(
    val from: Instant? = null,
    val to: Instant? = null,
    val lookback: Duration? = null,
)
