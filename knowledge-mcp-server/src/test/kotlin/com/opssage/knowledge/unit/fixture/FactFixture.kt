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
import com.opssage.knowledge.model.Fact
import com.opssage.knowledge.model.FactStatus
import com.opssage.knowledge.model.FactVerdict
import net.datafaker.Faker

import java.util.UUID

object FactFixture {

    private val faker = Faker()

    fun fact(
        id: String? = UUID.randomUUID().toString(),
        serviceId: String = faker.app().name(),
        symptom: String = faker.lorem().sentence(),
        rootCause: String = faker.lorem().sentence(),
        status: FactStatus = FactStatus.PROPOSED,
        verdict: FactVerdict = FactVerdict.CONFIRMED_CAUSE,
        confidence: Confidence = Confidence.MEDIUM,
    ) = Fact(
        id = id,
        serviceId = serviceId,
        symptom = symptom,
        rootCause = rootCause,
        status = status,
        verdict = verdict,
        confidence = confidence,
    )
}
