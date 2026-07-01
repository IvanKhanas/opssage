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

import com.opssage.knowledge.model.Criticality
import com.opssage.knowledge.model.ServiceProfile
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateServiceProfileRequest(
    @field:NotBlank
    @field:Size(max = 128)
    val serviceId: String,
    @field:NotBlank
    @field:Size(max = 256)
    val displayName: String,
    @field:NotBlank
    @field:Size(max = 4_000)
    val description: String,
    @field:NotBlank
    @field:Size(max = 128)
    val team: String,
    val criticality: Criticality,
    @field:Size(max = 100)
    val dependencies: List<
        @NotBlank
        @Size(max = 128)
        String,
    > = emptyList(),
    @field:Size(max = 50)
    val contacts: List<
        @NotBlank
        @Size(max = 256)
        String,
    > = emptyList(),
    @field:Size(max = 50)
    val tags: List<
        @NotBlank
        @Size(max = 64)
        String,
    > = emptyList(),
) {

    fun toServiceProfile(): ServiceProfile =
        ServiceProfile(
            serviceId = serviceId,
            displayName = displayName,
            description = description,
            team = team,
            criticality = criticality,
            dependencies = dependencies,
            contacts = contacts,
            tags = tags,
        )
}
