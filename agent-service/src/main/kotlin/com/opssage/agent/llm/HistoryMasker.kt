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
package com.opssage.agent.llm

import com.opssage.agent.masking.PiiMasker

import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.stereotype.Component

@Component
class HistoryMasker(
    private val masker: PiiMasker,
) {

    fun toMessages(history: List<ConversationTurn>): List<Message> =
        history.map { toMessage(it) }

    private fun toMessage(turn: ConversationTurn): Message {
        val masked = masker.mask(turn.content).text
        return when (turn.role) {
            TurnRole.USER -> UserMessage(masked)
            TurnRole.ASSISTANT -> AssistantMessage(masked)
        }
    }
}
