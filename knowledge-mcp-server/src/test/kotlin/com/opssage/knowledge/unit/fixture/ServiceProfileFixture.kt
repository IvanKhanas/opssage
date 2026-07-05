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

import com.opssage.knowledge.model.Criticality
import com.opssage.knowledge.model.ServiceProfile
import net.datafaker.Faker

import java.util.UUID

object ServiceProfileFixture {

    private val faker = Faker()

    fun profile(
        id: String? = UUID.randomUUID().toString(),
        serviceId: String = faker.app().name(),
        displayName: String = faker.app().name(),
        description: String = faker.lorem().sentence(),
        team: String = faker.team().name(),
        criticality: Criticality = Criticality.MEDIUM,
        upstreamServices: List<String> = emptyList(),
        downstreamServices: List<String> = emptyList(),
    ) = ServiceProfile(
        id = id,
        serviceId = serviceId,
        displayName = displayName,
        description = description,
        team = team,
        criticality = criticality,
        upstreamServices = upstreamServices,
        downstreamServices = downstreamServices,
    )
}
