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

import com.opssage.agent.investigation.InvestigationReport

data class ConversationMessageResultEvent(
    val metadata: ConversationMessageResultMetadata,
    val status: InvestigationResultStatus,
    val result: InvestigationResultPayload? = null,
)

data class ConversationMessageResultMetadata(
    val messageId: String,
    val requestId: String,
)

fun InvestigationReport.toMessageResultEvent(
    command: ConversationMessageCommand,
): ConversationMessageResultEvent =
    ConversationMessageResultEvent(
        metadata =
            ConversationMessageResultMetadata(
                messageId = command.metadata.messageId,
                requestId = command.metadata.requestId,
            ),
        status = InvestigationResultStatus.COMPLETED,
        result =
            InvestigationResultPayload(
                conversationId = conversationId,
                summary = summary,
                confidence = confidence,
                evidence = evidence,
            ),
    )

fun failedMessageResultEvent(
    command: ConversationMessageCommand,
): ConversationMessageResultEvent =
    ConversationMessageResultEvent(
        metadata =
            ConversationMessageResultMetadata(
                messageId = command.metadata.messageId,
                requestId = command.metadata.requestId,
            ),
        status = InvestigationResultStatus.FAILED,
    )
