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
package com.opssage.knowledge.model

import java.time.Instant

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "runbooks")
data class Runbook(
    @Id val id: String? = null,
    @Indexed val serviceId: String,
    val title: String,
    val alertName: String? = null,
    val triggerType: String? = null,
    val description: String,
    val symptoms: List<String> = emptyList(),
    val steps: List<String>,
    val recommendedTools: List<String> = emptyList(),
    val dangerNotes: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
