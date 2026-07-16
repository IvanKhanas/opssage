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
package com.opssage.admin.service

import com.opssage.admin.dto.CreateInvestigationRequest
import com.opssage.admin.messaging.InvestigationResultEvent
import com.opssage.admin.model.InvestigationReportSnapshot
import com.opssage.admin.model.InvestigationRequestCommand
import com.opssage.admin.model.InvestigationRequestOwner
import com.opssage.admin.model.InvestigationRequestPayload
import com.opssage.admin.model.InvestigationRequestRecord
import com.opssage.admin.model.InvestigationRequestResult
import com.opssage.admin.model.InvestigationRequestState
import com.opssage.admin.model.InvestigationRequestStatus
import com.opssage.admin.model.InvestigationRequestTimestamps
import com.opssage.admin.repository.InvestigationRequestRepository
import com.opssage.admin.security.UserPrincipal

import java.time.Clock

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class InvestigationRequestStore(
    private val repository: InvestigationRequestRepository,
    private val clock: Clock,
) {

    fun create(
        requestId: String,
        request: CreateInvestigationRequest,
        principal: UserPrincipal,
    ): InvestigationRequestRecord =
        repository.save(
            InvestigationRequestRecord(
                command = request.toRecordCommand(requestId, principal),
                state =
                    InvestigationRequestState(
                        status = InvestigationRequestStatus.ACCEPTED,
                        timestamps =
                            InvestigationRequestTimestamps(
                                clock.instant(),
                            ),
                    ),
            ),
        )

    fun complete(event: InvestigationResultEvent) {
        val record =
            repository.findByCommandRequestId(event.requestId)
                ?: return
        if (record.state.status != InvestigationRequestStatus.ACCEPTED) {
            return
        }
        repository.save(record.withResult(event, clock))
    }

    fun findOwned(
        requestId: String,
        principal: UserPrincipal,
    ): InvestigationRequestRecord? =
        repository
            .findByCommandRequestId(requestId)
            ?.takeIf { it.command.requester.userId == principal.userId }

    fun listOwned(
        principal: UserPrincipal,
        limit: Int,
    ): List<InvestigationRequestRecord> =
        repository.findByCommandRequesterUserId(
            principal.userId,
            PageRequest.of(0, limit.coerceIn(1, MAX_PAGE_SIZE)),
        )

    private fun CreateInvestigationRequest.toRecordCommand(
        requestId: String,
        principal: UserPrincipal,
    ): InvestigationRequestCommand =
        InvestigationRequestCommand(
            requestId = requestId,
            requester =
                InvestigationRequestOwner(
                    userId = principal.userId,
                    roles = principal.roles.mapTo(mutableSetOf()) { it.name },
                ),
            payload =
                InvestigationRequestPayload(
                    title = requireNotNull(investigation).title,
                    investigationType = investigation.investigationType,
                    input = investigation.input,
                ),
        )

    private companion object {
        const val MAX_PAGE_SIZE = 100
    }
}

private fun InvestigationRequestRecord.withResult(
    event: InvestigationResultEvent,
    clock: Clock,
): InvestigationRequestRecord =
    copy(
        state =
            state.copy(
                status = event.status,
                result = event.toStoredResult(),
                timestamps =
                    state.timestamps.copy(updatedAt = clock.instant()),
            ),
    )

private fun InvestigationResultEvent.toStoredResult():
    InvestigationRequestResult? =
    result?.let {
        InvestigationRequestResult(
            conversationId = it.conversationId,
            report =
                InvestigationReportSnapshot(
                    summary = it.summary,
                    confidence = it.confidence,
                    evidence = it.evidence,
                ),
        )
    }
