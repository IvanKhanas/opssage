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
package com.opssage.sre.unit

import com.opssage.sre.kubernetes.KubernetesAssembler
import com.opssage.sre.model.ClusterEvent
import com.opssage.sre.model.Confidence
import com.opssage.sre.model.PodStatus
import com.opssage.sre.model.ServiceKubernetesState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KubernetesAssemblerTest {

    private val assembler = KubernetesAssembler()

    @Test
    fun `correlates events to service pods and counts warnings`() {
        val state =
            ServiceKubernetesState(
                pods =
                    listOf(
                        PodStatus(
                            "deposit-service-abc",
                            "Running",
                            true,
                            0,
                            "",
                        ),
                        PodStatus(
                            "deposit-service-def",
                            "Pending",
                            false,
                            5,
                            "CrashLoopBackOff",
                        ),
                    ),
                events =
                    listOf(
                        event("deposit-service-abc", "Warning"),
                        event("deposit-service", "Normal"),
                        event("unrelated-xyz", "Warning"),
                    ),
            )

        val result = assembler.assemble("deposit-service", "banking", state)

        assertThat(result.pods).hasSize(2)
        assertThat(result.notReadyPodCount).isEqualTo(1)
        assertThat(result.events).hasSize(2)
        assertThat(result.warningCount).isEqualTo(1)
        assertThat(result.events.map { it.involvedObject })
            .contains("Pod/deposit-service-abc")
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun `reports low confidence when no pods are found`() {
        val state = ServiceKubernetesState(emptyList(), emptyList())

        val result = assembler.assemble("deposit-service", "banking", state)

        assertThat(result.pods).isEmpty()
        assertThat(result.confidence).isEqualTo(Confidence.LOW)
    }

    private fun event(
        objectName: String,
        type: String,
    ): ClusterEvent =
        ClusterEvent(
            type = type,
            reason = "BackOff",
            message = "event on $objectName",
            objectKind = "Pod",
            objectName = objectName,
            count = 1,
            lastSeen = "2026-07-06T10:00:00Z",
        )
}
