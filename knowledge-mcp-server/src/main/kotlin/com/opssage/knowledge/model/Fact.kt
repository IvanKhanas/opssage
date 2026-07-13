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

import com.fasterxml.jackson.annotation.JsonIgnore

import java.time.Instant

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.domain.Vector
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "facts")
data class Fact(
    @Id val id: String? = null,
    @Version val version: Long? = null,
    @Indexed val serviceId: String,
    val symptom: String,
    val rootCause: String,
    val resolution: String? = null,
    val status: FactStatus = FactStatus.PROPOSED,
    val verdict: FactVerdict = FactVerdict.CONFIRMED_CAUSE,
    val confidence: Confidence = Confidence.MEDIUM,
    val investigationId: String? = null,
    val createdAt: Instant = Instant.now(),
    val approvedBy: String? = null,
    val approvedAt: Instant? = null,
    @get:JsonIgnore
    val embedding: Vector? = null,
)
