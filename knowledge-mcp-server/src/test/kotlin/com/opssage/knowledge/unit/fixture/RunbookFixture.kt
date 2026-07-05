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

import com.opssage.knowledge.model.Runbook
import net.datafaker.Faker

import java.util.UUID

object RunbookFixture {

    private val faker = Faker()

    fun runbook(
        id: String? = UUID.randomUUID().toString(),
        serviceId: String = faker.app().name(),
        title: String = faker.lorem().sentence(3),
        alertName: String? = null,
        description: String = faker.lorem().paragraph(),
        steps: List<String> =
            listOf(
                faker.lorem().sentence(),
                faker.lorem().sentence(),
            ),
    ) = Runbook(
        id = id,
        serviceId = serviceId,
        title = title,
        alertName = alertName,
        description = description,
        steps = steps,
    )
}
