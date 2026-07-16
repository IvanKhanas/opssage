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
package com.opssage.agent.messaging

import com.opssage.agent.investigation.InvestigationReport
import com.opssage.agent.model.Confidence

data class InvestigationResultEvent(
    val requestId: String,
    val status: InvestigationResultStatus,
    val result: InvestigationResultPayload? = null,
)

data class InvestigationResultPayload(
    val conversationId: String,
    val summary: String,
    val confidence: Confidence,
    val evidence: List<String>,
)

enum class InvestigationResultStatus {
    COMPLETED,
    FAILED,
}

fun InvestigationReport.toResultEvent(
    requestId: String,
): InvestigationResultEvent =
    InvestigationResultEvent(
        requestId = requestId,
        status = InvestigationResultStatus.COMPLETED,
        result =
            InvestigationResultPayload(
                conversationId = conversationId,
                summary = summary,
                confidence = confidence,
                evidence = evidence,
            ),
    )

fun failedResultEvent(requestId: String): InvestigationResultEvent =
    InvestigationResultEvent(
        requestId = requestId,
        status = InvestigationResultStatus.FAILED,
    )
