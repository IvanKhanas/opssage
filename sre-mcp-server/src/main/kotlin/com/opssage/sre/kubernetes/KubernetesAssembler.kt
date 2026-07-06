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
package com.opssage.sre.kubernetes

import com.opssage.sre.dto.ClusterEventView
import com.opssage.sre.dto.KubernetesEventsResult
import com.opssage.sre.dto.PodStatusView
import com.opssage.sre.model.ClusterEvent
import com.opssage.sre.model.PodStatus
import com.opssage.sre.model.ServiceKubernetesState
import com.opssage.sre.util.ConfidenceCalculator

import org.springframework.stereotype.Component

@Component
class KubernetesAssembler {

    fun assemble(
        service: String,
        namespace: String,
        state: ServiceKubernetesState,
    ): KubernetesEventsResult {
        val podNames = state.pods.mapTo(HashSet()) { it.name }
        val relevant =
            state.events
                .filter { related(it, service, podNames) }
                .sortedByDescending { it.lastSeen }
        val notReady = state.pods.count { !it.ready }
        val warnings = relevant.count { it.type == WARNING }
        return KubernetesEventsResult(
            service = service,
            namespace = namespace,
            pods = state.pods.map(::toPodView),
            events = relevant.map(::toEventView),
            warningCount = warnings,
            notReadyPodCount = notReady,
            summary = summary(service, state.pods.size, notReady, warnings),
            confidence =
                ConfidenceCalculator.of(
                    if (state.pods.isEmpty()) 0 else 1,
                    1,
                ),
        )
    }

    private fun related(
        event: ClusterEvent,
        service: String,
        podNames: Set<String>,
    ): Boolean =
        event.objectName in podNames ||
            event.objectName == service ||
            event.objectName.startsWith("$service-")

    private fun toPodView(pod: PodStatus): PodStatusView =
        PodStatusView(
            name = pod.name,
            phase = pod.phase,
            ready = pod.ready,
            restartCount = pod.restartCount,
            reason = pod.reason,
        )

    private fun toEventView(event: ClusterEvent): ClusterEventView =
        ClusterEventView(
            type = event.type,
            reason = event.reason,
            message = event.message,
            involvedObject = "${event.objectKind}/${event.objectName}",
            count = event.count,
            lastSeen = event.lastSeen,
        )

    private fun summary(
        service: String,
        pods: Int,
        notReady: Int,
        warnings: Int,
    ): String =
        "Kubernetes state for $service: $pods pods ($notReady not ready), " +
            "$warnings warning events."

    private companion object {
        const val WARNING = "Warning"
    }
}
