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

import com.opssage.agent.outbox.OutboxDestination
import com.opssage.agent.outbox.OutboxEvent
import com.opssage.agent.outbox.OutboxEventRepository
import com.opssage.agent.outbox.OutboxMessage
import com.opssage.agent.outbox.OutboxStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import java.time.Duration

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
class OutboxClaimRepositoryTest {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun mongoProps(registry: DynamicPropertyRegistry) =
            TestContainersConfiguration.configure(registry)
    }

    @Autowired
    lateinit var repository: OutboxEventRepository

    @Autowired
    lateinit var mongo: MongoTemplate

    @BeforeEach
    fun cleanUp() {
        mongo.dropCollection(OutboxEvent::class.java)
    }

    @Test
    fun `claim marks event in progress and hides it from second claim`() {
        repository.save(event())

        val first = repository.claimPending(1, Duration.ofMinutes(2))
        val second = repository.claimPending(1, Duration.ofMinutes(2))

        val claimed = first.single()
        val lease = claimed.state.delivery.lease

        assertThat(first).hasSize(1)
        assertThat(claimed.state.status)
            .isEqualTo(OutboxStatus.IN_PROGRESS)
        assertThat(lease.lockedUntil).isNotNull()
        assertThat(second).isEmpty()
    }

    private fun event(): OutboxEvent =
        OutboxEvent(
            message =
                OutboxMessage(
                    destination =
                        OutboxDestination(
                            topic = "topic",
                            key = "key",
                        ),
                    payload = "{}",
                    eventType = "TestEvent",
                ),
        )
}
