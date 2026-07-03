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

import com.opssage.knowledge.model.KnownIncident
import net.datafaker.Faker

import java.time.Instant
import java.util.UUID

object KnownIncidentFixture {

    private val faker = Faker()

    fun knownIncident(
        id: String? = UUID.randomUUID().toString(),
        serviceId: String = faker.app().name(),
        title: String = faker.lorem().sentence(3),
        occurredAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
        symptoms: List<String> =
            listOf(
                faker.lorem().sentence(),
                faker.lorem().sentence(),
            ),
        rootCause: String = faker.lorem().paragraph(),
        resolution: String? = faker.lorem().sentence(),
        evidence: List<String> = listOf("traceId=abc123"),
        relatedServices: List<String> = emptyList(),
    ) = KnownIncident(
        id = id,
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
