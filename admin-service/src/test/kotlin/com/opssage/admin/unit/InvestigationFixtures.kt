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
package com.opssage.admin.unit

import com.opssage.admin.model.Confidence
import com.opssage.admin.model.InvestigationReportSnapshot
import com.opssage.admin.model.InvestigationRequestCommand
import com.opssage.admin.model.InvestigationRequestOwner
import com.opssage.admin.model.InvestigationRequestPayload
import com.opssage.admin.model.InvestigationRequestRecord
import com.opssage.admin.model.InvestigationRequestResult
import com.opssage.admin.model.InvestigationRequestState
import com.opssage.admin.model.InvestigationRequestStatus
import com.opssage.admin.model.InvestigationRequestTimestamps
import com.opssage.admin.model.InvestigationType

object InvestigationFixtures {

    fun acceptedRecord(): InvestigationRequestRecord =
        record(
            InvestigationRequestState(
                status = InvestigationRequestStatus.ACCEPTED,
                timestamps = InvestigationRequestTimestamps(),
            ),
        )

    fun completedRecord(): InvestigationRequestRecord =
        record(
            InvestigationRequestState(
                status = InvestigationRequestStatus.COMPLETED,
                result =
                    InvestigationRequestResult(
                        conversationId = "conv-1",
                        report =
                            InvestigationReportSnapshot(
                                summary = "deposit degraded",
                                confidence = Confidence.HIGH,
                                evidence = listOf("metric"),
                            ),
                    ),
                timestamps = InvestigationRequestTimestamps(),
            ),
        )

    private fun record(
        state: InvestigationRequestState,
    ): InvestigationRequestRecord =
        InvestigationRequestRecord(
            command =
                InvestigationRequestCommand(
                    requestId = "req-1",
                    requester =
                        InvestigationRequestOwner(
                            userId = "user-1",
                            roles = setOf("SRE"),
                        ),
                    payload =
                        InvestigationRequestPayload(
                            title = "deposit alert",
                            investigationType =
                                InvestigationType.ALERT_INVESTIGATION,
                            input = "HighErrorRate fired",
                        ),
                ),
            state = state,
        )
}
