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
package com.opssage.sre.mcp

import com.opssage.sre.config.McpProperties
import com.opssage.sre.dto.KubernetesEventsResult
import com.opssage.sre.kubernetes.KubernetesService
import com.opssage.sre.util.Identifiers
import com.opssage.sre.util.blockingGet

import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component

@Component
class KubernetesMcpTools(
    private val kubernetesService: KubernetesService,
    private val mcp: McpProperties,
) : McpToolSet {

    @Tool(
        description =
            "Get the current Kubernetes state for a service in a " +
                "namespace: the status of its pods (phase, readiness, " +
                "restart count and any waiting reason such as " +
                "CrashLoopBackOff) and the recent cluster events involving " +
                "those pods. Use it to see whether a service is failing to " +
                "schedule, crashing or being restarted; the caller judges " +
                "the root cause.",
    )
    fun getKubernetesServiceEvents(
        service: String,
        namespace: String,
    ): KubernetesEventsResult {
        Identifiers.require("service", service)
        Identifiers.require("namespace", namespace)
        return kubernetesService
            .serviceEvents(service, namespace)
            .blockingGet(
                mcp.callTimeout,
                "reading kubernetes events for $service",
            )
    }
}
