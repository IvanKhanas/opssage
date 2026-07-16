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
package com.opssage.admin.messaging

import com.opssage.admin.config.AdminKafkaProperties
import com.opssage.admin.outbox.OutboxDestination
import com.opssage.admin.outbox.OutboxMessage
import com.opssage.admin.outbox.OutboxService
import tools.jackson.databind.ObjectMapper

import org.springframework.stereotype.Component

@Component
class ConversationMessageOutbox(
    private val outbox: OutboxService,
    private val objectMapper: ObjectMapper,
    private val kafka: AdminKafkaProperties,
) {

    fun enqueue(command: ConversationMessageCommand) {
        outbox.enqueue(
            OutboxMessage(
                destination =
                    OutboxDestination(
                        topic =
                            kafka.topics
                                .messages
                                .investigationMessagesTopic,
                        key = command.metadata.messageId,
                    ),
                payload = objectMapper.writeValueAsString(command),
                eventType = CONVERSATION_MESSAGE_REQUESTED,
            ),
        )
    }

    private companion object {
        const val CONVERSATION_MESSAGE_REQUESTED =
            "ConversationMessageRequested"
    }
}
