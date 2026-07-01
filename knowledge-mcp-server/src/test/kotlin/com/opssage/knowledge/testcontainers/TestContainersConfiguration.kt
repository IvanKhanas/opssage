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
package com.opssage.knowledge.testcontainers

import org.testcontainers.containers.MongoDBContainer

import org.springframework.test.context.DynamicPropertyRegistry

object TestContainersConfiguration {

    val mongoContainer: MongoDBContainer =
        MongoDBContainer("mongo:7.0")
            .also { it.start() }

    fun configure(registry: DynamicPropertyRegistry) {
        registry.add("spring.mongodb.uri") { mongoContainer.replicaSetUrl }
    }
}
