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
package com.opssage.agent.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("agent.kafka")
data class AgentKafkaProperties(
    val topics: AgentKafkaTopics,
    val dlq: AgentKafkaDlqProperties,
)

data class AgentKafkaTopics(
    val investigations: AgentInvestigationTopics,
    val messages: AgentMessageTopics,
)

data class AgentInvestigationTopics(
    val investigationRequestsTopic: String,
    val investigationResultsTopic: String,
)

data class AgentMessageTopics(
    val investigationMessagesTopic: String,
    val investigationMessageResultsTopic: String,
)

data class AgentKafkaDlqProperties(
    val investigationRequestsTopic: String,
    val investigationMessagesTopic: String,
)
