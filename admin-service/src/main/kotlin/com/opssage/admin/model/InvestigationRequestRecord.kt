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
package com.opssage.admin.model

import java.time.Instant

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document("investigation_requests")
data class InvestigationRequestRecord(
    @Id
    val id: String? = null,
    val command: InvestigationRequestCommand,
    val state: InvestigationRequestState,
)

data class InvestigationRequestCommand(
    @Indexed(unique = true)
    val requestId: String,
    val requester: InvestigationRequestOwner,
    val payload: InvestigationRequestPayload,
)

data class InvestigationRequestOwner(
    @Indexed
    val userId: String,
    val roles: Set<String>,
)

data class InvestigationRequestPayload(
    val title: String,
    val investigationType: InvestigationType,
    val input: String,
)

data class InvestigationRequestState(
    val status: InvestigationRequestStatus,
    val result: InvestigationRequestResult? = null,
    val timestamps: InvestigationRequestTimestamps,
)

data class InvestigationRequestResult(
    val conversationId: String? = null,
    val report: InvestigationReportSnapshot? = null,
)

data class InvestigationReportSnapshot(
    val summary: String,
    val confidence: Confidence,
    val evidence: List<String>,
)

data class InvestigationRequestTimestamps(
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
)
