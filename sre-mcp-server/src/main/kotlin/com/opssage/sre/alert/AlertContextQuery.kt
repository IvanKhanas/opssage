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
package com.opssage.sre.alert

import com.opssage.sre.config.QueryProperties
import com.opssage.sre.dto.AlertContextResult
import com.opssage.sre.dto.KubernetesEventsResult
import com.opssage.sre.dto.ServiceHealthResult
import com.opssage.sre.dto.TimeWindowView
import com.opssage.sre.dto.TopLogErrorsResult
import com.opssage.sre.kubernetes.KubernetesService
import com.opssage.sre.logs.LogQuery
import com.opssage.sre.logs.LogsService
import com.opssage.sre.metrics.ServiceHealthQuery
import com.opssage.sre.model.Confidence
import com.opssage.sre.time.TimeWindow
import com.opssage.sre.util.ConfidenceCalculator
import reactor.core.publisher.Mono

import org.springframework.stereotype.Component

@Component
class AlertContextQuery(
    private val health: ServiceHealthQuery,
    private val logs: LogsService,
    private val kubernetes: KubernetesService,
    private val query: QueryProperties,
) {

    fun run(
        service: String,
        namespace: String,
        window: TimeWindow,
    ): Mono<AlertContextResult> {
        val logQuery = LogQuery(service, namespace, query.alertLogErrors)
        return Mono
            .zip(
                health
                    .run(service, namespace, window)
                    .onErrorReturn(emptyHealth(service, namespace, window)),
                logs
                    .topLogErrors(logQuery, window)
                    .onErrorReturn(emptyErrors(service, namespace, window)),
                kubernetes
                    .serviceEvents(service, namespace)
                    .onErrorReturn(emptyKubernetes(service, namespace)),
            ).map { parts ->
                combine(
                    service,
                    namespace,
                    window,
                    parts.t1,
                    parts.t2,
                    parts.t3,
                )
            }
    }

    private fun combine(
        service: String,
        namespace: String,
        window: TimeWindow,
        health: ServiceHealthResult,
        errors: TopLogErrorsResult,
        kubernetes: KubernetesEventsResult,
    ): AlertContextResult =
        AlertContextResult(
            service = service,
            namespace = namespace,
            window = TimeWindowView.of(window),
            health = health,
            topErrors = errors,
            kubernetes = kubernetes,
            summary = summary(service, errors, kubernetes),
            confidence =
                ConfidenceCalculator.lowest(
                    listOf(
                        health.confidence,
                        errors.confidence,
                        kubernetes.confidence,
                    ),
                ),
        )

    private fun summary(
        service: String,
        errors: TopLogErrorsResult,
        kubernetes: KubernetesEventsResult,
    ): String =
        "Alert context for $service: ${errors.topErrors.size} log error " +
            "fingerprints, ${kubernetes.warningCount} warning events, " +
            "${kubernetes.notReadyPodCount} pods not ready."

    private fun emptyHealth(
        service: String,
        namespace: String,
        window: TimeWindow,
    ): ServiceHealthResult =
        ServiceHealthResult(
            service = service,
            namespace = namespace,
            window = TimeWindowView.of(window),
            signals = emptyList(),
            summary = "Health source unavailable.",
            confidence = Confidence.LOW,
        )

    private fun emptyErrors(
        service: String,
        namespace: String,
        window: TimeWindow,
    ): TopLogErrorsResult =
        TopLogErrorsResult(
            service = service,
            namespace = namespace,
            window = TimeWindowView.of(window),
            topErrors = emptyList(),
            summary = "Log source unavailable.",
            confidence = Confidence.LOW,
        )

    private fun emptyKubernetes(
        service: String,
        namespace: String,
    ): KubernetesEventsResult =
        KubernetesEventsResult(
            service = service,
            namespace = namespace,
            pods = emptyList(),
            events = emptyList(),
            warningCount = 0,
            notReadyPodCount = 0,
            summary = "Kubernetes source unavailable.",
            confidence = Confidence.LOW,
        )
}
