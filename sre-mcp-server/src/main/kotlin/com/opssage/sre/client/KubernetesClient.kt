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
package com.opssage.sre.client

import com.opssage.sre.config.KubernetesProperties
import com.opssage.sre.config.QueryProperties
import com.opssage.sre.model.ClusterEvent
import com.opssage.sre.model.PodStatus
import com.opssage.sre.model.ServiceKubernetesState
import io.github.oshai.kotlinlogging.KotlinLogging
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

private val log = KotlinLogging.logger {}

@Component
class KubernetesClient(
    private val kubernetesWebClient: WebClient,
    private val kubernetes: KubernetesProperties,
    private val query: QueryProperties,
) {

    fun serviceState(
        service: String,
        namespace: String,
    ): Mono<ServiceKubernetesState> =
        pods(service, namespace)
            .flatMap { pods ->
                events(service, namespace, pods).map { events ->
                    ServiceKubernetesState(pods, events)
                }
            }.doOnError { error ->
                log.atWarn {
                    message = "Kubernetes query failed"
                    payload =
                        mapOf("service" to service, "namespace" to namespace)
                    cause = error
                }
            }

    private fun pods(
        service: String,
        namespace: String,
    ): Mono<List<PodStatus>> =
        kubernetesWebClient
            .get()
            .uri { builder ->
                builder
                    .path("/api/v1/namespaces/{ns}/pods")
                    .queryParam(
                        "labelSelector",
                        "${kubernetes.appLabel}=$service",
                    ).queryParam("limit", query.maxPods)
                    .build(namespace)
            }.retrieve()
            .bodyToMono<K8sPodList>()
            .map { list ->
                list.items
                    .take(query.maxPods)
                    .map(::toPod)
            }

    private fun events(
        service: String,
        namespace: String,
        pods: List<PodStatus>,
    ): Mono<List<ClusterEvent>> {
        val objectNames =
            (pods.map { it.name } + service)
                .distinct()
                .take(query.maxPods + 1)
        return Flux
            .fromIterable(objectNames)
            .flatMap(
                { objectName -> eventsForObject(namespace, objectName) },
                query.maxPods,
            ).collectList()
            .map { events ->
                events
                    .flatten()
                    .take(query.maxEvents)
            }
    }

    private fun eventsForObject(
        namespace: String,
        objectName: String,
    ): Mono<List<ClusterEvent>> =
        kubernetesWebClient
            .get()
            .uri { builder ->
                builder
                    .path("/api/v1/namespaces/{ns}/events")
                    .queryParam(
                        "fieldSelector",
                        "involvedObject.name=$objectName",
                    ).queryParam("limit", query.maxEvents)
                    .build(namespace)
            }.retrieve()
            .bodyToMono<K8sEventList>()
            .map { list -> list.items.map(::toEvent) }
            .defaultIfEmpty(emptyList())

    private fun toPod(pod: K8sPod): PodStatus {
        val containers = pod.status.containerStatuses
        return PodStatus(
            name = pod.metadata.name,
            phase = pod.status.phase,
            ready = containers.isNotEmpty() && containers.all { it.ready },
            restartCount = containers.sumOf { it.restartCount },
            reason = reason(pod),
        )
    }

    private fun reason(pod: K8sPod): String {
        if (pod.status.reason.isNotBlank()) return pod.status.reason
        return pod.status.containerStatuses
            .firstNotNullOfOrNull { it.state.waiting?.reason }
            .orEmpty()
    }

    private fun toEvent(event: K8sEvent): ClusterEvent =
        ClusterEvent(
            type = event.type,
            reason = event.reason,
            message = event.message,
            objectKind = event.involvedObject.kind,
            objectName = event.involvedObject.name,
            count = event.count,
            lastSeen = (event.lastTimestamp ?: event.eventTime).orEmpty(),
        )
}
