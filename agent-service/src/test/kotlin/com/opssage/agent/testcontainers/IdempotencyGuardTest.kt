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
package com.opssage.agent.testcontainers

import com.opssage.agent.messaging.IdempotencyGuard
import com.opssage.agent.messaging.ProcessedMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "spring.ai.mcp.client.enabled=false",
        "spring.ai.openai.api-key=test-key",
        "spring.kafka.listener.auto-startup=false",
    ],
)
class IdempotencyGuardTest {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun mongoProps(registry: DynamicPropertyRegistry) =
            TestContainersConfiguration.configure(registry)
    }

    @Autowired
    lateinit var guard: IdempotencyGuard

    @Autowired
    lateinit var mongo: MongoTemplate

    @BeforeEach
    fun cleanUp() {
        mongo.dropCollection(ProcessedMessage::class.java)
    }

    @Test
    fun `try start returns false for duplicate key`() {
        assertThat(guard.tryStart("investigation:req-1")).isTrue()
        assertThat(guard.tryStart("investigation:req-1")).isFalse()
    }
}
