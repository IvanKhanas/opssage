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
package com.opssage.knowledge.dto

import com.opssage.knowledge.model.KnownIncident
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

import java.time.Instant

data class CreateKnownIncidentRequest(
    @field:NotBlank
    @field:Size(max = 128)
    val serviceId: String,
    @field:NotBlank
    @field:Size(max = 256)
    val title: String,
    @field:NotNull
    val occurredAt: Instant,
    @field:Size(min = 1, max = 100)
    val symptoms: List<
        @NotBlank
        @Size(max = 2_000)
        String,
    >,
    @field:NotBlank
    @field:Size(max = 4_000)
    val rootCause: String,
    @field:Size(max = 4_000)
    val resolution: String? = null,
    @field:Size(max = 100)
    val evidence: List<
        @NotBlank
        @Size(max = 2_000)
        String,
    > = emptyList(),
    @field:Size(max = 100)
    val relatedServices: List<
        @NotBlank
        @Size(max = 128)
        String,
    > = emptyList(),
) {

    fun toIncident(): KnownIncident =
        KnownIncident(
            serviceId = serviceId,
            title = title,
            occurredAt = occurredAt,
            symptoms = symptoms,
            rootCause = rootCause,
            resolution = resolution,
            evidence = evidence,
            relatedServices = relatedServices,
        )
}
