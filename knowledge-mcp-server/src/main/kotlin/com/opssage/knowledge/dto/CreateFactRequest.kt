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

import com.opssage.knowledge.model.Confidence
import com.opssage.knowledge.model.FactProposal
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateFactRequest(
    @field:NotBlank
    @field:Size(max = 128)
    val serviceId: String,
    @field:NotBlank
    @field:Size(max = 2_000)
    val symptom: String,
    @field:NotBlank
    @field:Size(max = 4_000)
    val rootCause: String,
    @field:Size(max = 4_000)
    val resolution: String? = null,
    @field:Size(max = 50)
    val tags: List<
        @NotBlank
        @Size(max = 64)
        String,
    > = emptyList(),
    val confidence: Confidence = Confidence.MEDIUM,
    @field:Size(max = 128)
    val investigationId: String? = null,
) {

    fun toProposal(): FactProposal =
        FactProposal(
            serviceId = serviceId,
            symptom = symptom,
            rootCause = rootCause,
            resolution = resolution,
            tags = tags,
            confidence = confidence,
            investigationId = investigationId,
        )
}
