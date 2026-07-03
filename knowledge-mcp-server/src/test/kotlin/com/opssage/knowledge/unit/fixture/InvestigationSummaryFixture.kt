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
package com.opssage.knowledge.unit.fixture

import com.opssage.knowledge.model.Confidence
import com.opssage.knowledge.model.InvestigationSummary
import net.datafaker.Faker

import java.util.UUID

object InvestigationSummaryFixture {

    private val faker = Faker()

    fun investigationSummary(
        id: String? = UUID.randomUUID().toString(),
        investigationId: String = UUID.randomUUID().toString(),
        serviceId: String = "payment-svc",
        summary: String = faker.lorem().sentence(),
        mostLikelyCause: String = faker.lorem().sentence(),
        confidence: Confidence = Confidence.MEDIUM,
        evidence: List<String> = listOf(faker.lorem().sentence()),
        recommendedActions: List<String> = listOf(faker.lorem().sentence()),
    ) = InvestigationSummary(
        id = id,
        investigationId = investigationId,
        serviceId = serviceId,
        summary = summary,
        mostLikelyCause = mostLikelyCause,
        confidence = confidence,
        evidence = evidence,
        recommendedActions = recommendedActions,
    )
}
