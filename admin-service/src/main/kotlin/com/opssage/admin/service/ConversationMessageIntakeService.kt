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

import com.opssage.admin.dto.CreateInvestigationMessageRequest
import com.opssage.admin.messaging.ConversationMessageBody
import com.opssage.admin.messaging.ConversationMessageCommand
import com.opssage.admin.messaging.ConversationMessageMetadata
import com.opssage.admin.messaging.ConversationMessageOutbox
import com.opssage.admin.messaging.ConversationMessagePayload
import com.opssage.admin.messaging.ConversationMessageTarget
import com.opssage.admin.messaging.InvestigationRequester
import com.opssage.admin.security.UserPrincipal

import java.time.Instant
import java.util.UUID

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class ConversationMessageIntakeService(
    private val requests: InvestigationRequestStore,
    private val messages: InvestigationMessageStore,
    private val outbox: ConversationMessageOutbox,
) {

    @Transactional
    fun submit(
        requestId: String,
        request: CreateInvestigationMessageRequest,
        principal: UserPrincipal,
    ): String {
        val investigation =
            requests.findOwned(requestId, principal)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val conversationId =
            investigation.state.result?.conversationId
                ?: throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "conversation_not_ready",
                )
        val messageId = UUID.randomUUID().toString()
        val messageRequest =
            MessageCreateRequest(
                messageId = messageId,
                principal = principal,
                requestId = requestId,
                conversationId = conversationId,
                input = request.toInput(),
            )
        messages.create(messageRequest)
        outbox.enqueue(command(messageRequest))
        return messageId
    }

    private fun command(
        request: MessageCreateRequest,
    ): ConversationMessageCommand =
        ConversationMessageCommand(
            metadata =
                ConversationMessageMetadata(
                    messageId = request.messageId,
                    requestId = request.requestId,
                    requestedAt = Instant.now(),
                ),
            requester =
                InvestigationRequester(
                    userId = request.principal.userId,
                    roles =
                        request.principal.roles.mapTo(
                            mutableSetOf(),
                        ) { it.name },
                ),
            body =
                ConversationMessageBody(
                    conversation =
                        ConversationMessageTarget(
                            request.conversationId,
                        ),
                    payload =
                        ConversationMessagePayload(request.input),
                ),
        )
}
