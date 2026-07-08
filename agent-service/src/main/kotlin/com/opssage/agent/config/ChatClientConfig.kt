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

import com.opssage.agent.masking.MaskingToolCallback
import com.opssage.agent.masking.PiiMasker
import io.github.oshai.kotlinlogging.KotlinLogging

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

private val log = KotlinLogging.logger {}

@Configuration
class ChatClientConfig {

    @Bean
    fun chatClient(
        builder: ChatClient.Builder,
        toolCallbackProviders: List<ToolCallbackProvider>,
        piiMasker: PiiMasker,
    ): ChatClient {
        val maskedCallbacks: Array<ToolCallback> =
            toolCallbackProviders
                .flatMap { it.toolCallbacks.toList() }
                .map { MaskingToolCallback(it, piiMasker) }
                .toTypedArray()
        log.atInfo {
            message = "Wrapped MCP tool callbacks with PII masking"
            payload = mapOf("count" to maskedCallbacks.size)
        }
        return builder
            .defaultToolCallbacks(*maskedCallbacks)
            .build()
    }
}
