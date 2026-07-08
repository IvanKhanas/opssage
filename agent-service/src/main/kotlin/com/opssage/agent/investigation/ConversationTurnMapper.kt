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
package com.opssage.agent.investigation

import com.opssage.agent.llm.ConversationTurn
import com.opssage.agent.llm.TurnRole
import com.opssage.agent.model.Message
import com.opssage.agent.model.MessageRole

object ConversationTurnMapper {

    fun toHistory(
        messages: List<Message>,
        maxMessages: Int,
    ): List<ConversationTurn> =
        messages
            .takeLast(maxMessages)
            .mapNotNull { toTurn(it) }

    private fun toTurn(message: Message): ConversationTurn? =
        when (message.role) {
            MessageRole.USER ->
                ConversationTurn(TurnRole.USER, message.content)
            MessageRole.ASSISTANT ->
                ConversationTurn(TurnRole.ASSISTANT, message.content)
            else -> null
        }
}
