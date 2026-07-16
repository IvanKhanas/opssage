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

import com.opssage.admin.model.Confidence
import com.opssage.admin.model.InvestigationRequestRecord
import com.opssage.admin.model.InvestigationRequestStatus
import com.opssage.admin.model.InvestigationType

import java.time.Instant

data class InvestigationResponse(
    val request: InvestigationRequestResponse,
    val state: InvestigationStateResponse,
)

data class InvestigationRequestResponse(
    val requestId: String,
    val title: String,
    val investigationType: InvestigationType,
)

data class InvestigationStateResponse(
    val status: InvestigationRequestStatus,
    val result: InvestigationResultResponse? = null,
    val updatedAt: Instant,
)

data class InvestigationResultResponse(
    val conversationId: String?,
    val summary: String?,
    val confidence: Confidence?,
    val evidence: List<String>,
)

fun InvestigationRequestRecord.toResponse(): InvestigationResponse =
    InvestigationResponse(
        request =
            InvestigationRequestResponse(
                requestId = command.requestId,
                title = command.payload.title,
                investigationType = command.payload.investigationType,
            ),
        state =
            InvestigationStateResponse(
                status = state.status,
                result = state.result?.toResponse(),
                updatedAt = state.timestamps.updatedAt,
            ),
    )

fun com.opssage.admin.model.InvestigationRequestResult.toResponse() =
    InvestigationResultResponse(
        conversationId = conversationId,
        summary = report?.summary,
        confidence = report?.confidence,
        evidence = report?.evidence.orEmpty(),
    )
