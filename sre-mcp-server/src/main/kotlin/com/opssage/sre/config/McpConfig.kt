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
package com.opssage.sre.config

import com.opssage.sre.mcp.AlertContextMcpTools
import com.opssage.sre.mcp.CorrectnessMcpTools
import com.opssage.sre.mcp.KubernetesMcpTools
import com.opssage.sre.mcp.LogsMcpTools
import com.opssage.sre.mcp.MetricsMcpTools
import com.opssage.sre.mcp.TracesMcpTools

import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class McpConfig {

    @Bean
    fun toolCallbackProvider(
        metricsMcpTools: MetricsMcpTools,
        logsMcpTools: LogsMcpTools,
        tracesMcpTools: TracesMcpTools,
        kubernetesMcpTools: KubernetesMcpTools,
        alertContextMcpTools: AlertContextMcpTools,
        correctnessMcpTools: CorrectnessMcpTools,
    ): ToolCallbackProvider =
        MethodToolCallbackProvider
            .builder()
            .toolObjects(
                metricsMcpTools,
                logsMcpTools,
                tracesMcpTools,
                kubernetesMcpTools,
                alertContextMcpTools,
                correctnessMcpTools,
            ).build()
}
