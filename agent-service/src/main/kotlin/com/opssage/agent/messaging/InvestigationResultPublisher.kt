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

import com.opssage.agent.config.AgentKafkaProperties
import com.opssage.agent.outbox.OutboxDestination
import com.opssage.agent.outbox.OutboxMessage
import com.opssage.agent.outbox.OutboxService
import tools.jackson.databind.ObjectMapper

import org.springframework.stereotype.Component

@Component
class InvestigationResultPublisher(
    private val outbox: OutboxService,
    private val objectMapper: ObjectMapper,
    private val properties: AgentKafkaProperties,
) {

    fun publish(event: InvestigationResultEvent) {
        outbox.enqueue(
            OutboxMessage(
                destination =
                    OutboxDestination(
                        topic =
                            properties.topics
                                .investigations
                                .investigationResultsTopic,
                        key = event.requestId,
                    ),
                payload = objectMapper.writeValueAsString(event),
                eventType = event.status.name,
            ),
        )
    }
}
