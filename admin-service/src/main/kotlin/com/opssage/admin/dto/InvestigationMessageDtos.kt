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

import com.opssage.admin.model.InvestigationMessageRecord
import com.opssage.admin.model.InvestigationRequestStatus
import jakarta.validation.constraints.NotBlank

import java.time.Instant

data class CreateInvestigationMessageRequest(
    @field:NotBlank
    val input: String?,
) {

    fun toInput(): String = requireNotNull(input)
}

data class InvestigationMessageAcceptedResponse(
    val messageId: String,
    val status: String,
)

data class InvestigationMessageResponse(
    val metadata: InvestigationMessageResponseMetadata,
    val input: String,
    val state: InvestigationMessageStateResponse,
)

data class InvestigationMessageResponseMetadata(
    val messageId: String,
    val requestId: String,
)

data class InvestigationMessageStateResponse(
    val status: InvestigationRequestStatus,
    val result: InvestigationResultResponse? = null,
    val updatedAt: Instant,
)

fun InvestigationMessageRecord.toResponse(): InvestigationMessageResponse =
    InvestigationMessageResponse(
        metadata =
            InvestigationMessageResponseMetadata(
                messageId = command.metadata.messageId,
                requestId = command.metadata.requestId,
            ),
        input = command.payload.input,
        state =
            InvestigationMessageStateResponse(
                status = state.status,
                result = state.result?.toResponse(),
                updatedAt = state.timestamps.updatedAt,
            ),
    )
