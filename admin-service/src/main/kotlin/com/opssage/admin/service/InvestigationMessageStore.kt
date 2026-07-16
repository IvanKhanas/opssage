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

import com.opssage.admin.messaging.ConversationMessageResultEvent
import com.opssage.admin.model.InvestigationMessageCommand
import com.opssage.admin.model.InvestigationMessageMetadata
import com.opssage.admin.model.InvestigationMessagePayload
import com.opssage.admin.model.InvestigationMessageRecord
import com.opssage.admin.model.InvestigationMessageState
import com.opssage.admin.model.InvestigationReportSnapshot
import com.opssage.admin.model.InvestigationRequestOwner
import com.opssage.admin.model.InvestigationRequestResult
import com.opssage.admin.model.InvestigationRequestStatus
import com.opssage.admin.model.InvestigationRequestTimestamps
import com.opssage.admin.repository.InvestigationMessageRepository
import com.opssage.admin.security.UserPrincipal

import java.time.Clock

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class InvestigationMessageStore(
    private val repository: InvestigationMessageRepository,
    private val clock: Clock,
) {

    fun create(request: MessageCreateRequest): InvestigationMessageRecord =
        repository.save(
            InvestigationMessageRecord(
                command = request.toCommand(),
                state =
                    InvestigationMessageState(
                        status = InvestigationRequestStatus.ACCEPTED,
                        timestamps =
                            InvestigationRequestTimestamps(clock.instant()),
                    ),
            ),
        )

    fun complete(event: ConversationMessageResultEvent) {
        val record =
            repository.findByMessageId(event.metadata.messageId)
                ?: return
        if (record.state.status != InvestigationRequestStatus.ACCEPTED) {
            return
        }
        repository.save(record.withResult(event, clock))
    }

    fun listOwned(
        requestId: String,
        principal: UserPrincipal,
        limit: Int,
    ): List<InvestigationMessageRecord> =
        repository.findByRequestIdAndUserId(
            requestId,
            principal.userId,
            PageRequest.of(0, limit.coerceIn(1, MAX_PAGE_SIZE)),
        )

    fun findOwned(
        messageId: String,
        principal: UserPrincipal,
    ): InvestigationMessageRecord? =
        repository.findByMessageIdAndUserId(
            messageId,
            principal.userId,
        )

    private fun MessageCreateRequest.toCommand(): InvestigationMessageCommand =
        InvestigationMessageCommand(
            metadata =
                InvestigationMessageMetadata(
                    messageId = messageId,
                    requestId = requestId,
                ),
            owner =
                InvestigationRequestOwner(
                    userId = principal.userId,
                    roles = principal.roles.mapTo(mutableSetOf()) { it.name },
                ),
            payload =
                InvestigationMessagePayload(
                    conversationId = conversationId,
                    input = input,
                ),
        )

    private companion object {
        const val MAX_PAGE_SIZE = 100
    }
}

private fun InvestigationMessageRecord.withResult(
    event: ConversationMessageResultEvent,
    clock: Clock,
): InvestigationMessageRecord =
    copy(
        state =
            state.copy(
                status = event.status,
                result = event.toStoredResult(),
                timestamps =
                    state.timestamps.copy(updatedAt = clock.instant()),
            ),
    )

private fun ConversationMessageResultEvent.toStoredResult():
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
