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

import java.time.Instant

data class ConversationMessageCommand(
    val metadata: ConversationMessageMetadata,
    val requester: InvestigationRequester? = null,
    val body: ConversationMessageBody,
)

data class ConversationMessageMetadata(
    val messageId: String,
    val requestId: String,
    val requestedAt: Instant,
)

data class ConversationMessageBody(
    val conversation: ConversationMessageTarget,
    val payload: ConversationMessagePayload,
)

data class ConversationMessageTarget(
    val conversationId: String,
)

data class ConversationMessagePayload(
    val input: String,
)
