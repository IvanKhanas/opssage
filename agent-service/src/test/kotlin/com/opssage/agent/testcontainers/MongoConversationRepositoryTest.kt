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

import com.opssage.agent.model.Conversation
import com.opssage.agent.model.InvestigationType
import com.opssage.agent.repository.ConversationRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "spring.ai.mcp.client.enabled=false",
        "spring.ai.openai.api-key=test-key",
    ],
)
class MongoConversationRepositoryTest {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun mongoProps(registry: DynamicPropertyRegistry) =
            TestContainersConfiguration.configure(registry)
    }

    @Autowired
    lateinit var repository: ConversationRepository

    @BeforeEach
    fun cleanUp() {
        repository.findAll().forEach { repository.deleteById(it.id!!) }
    }

    @Test
    fun `saves and reads back a conversation by id`() {
        val saved =
            repository.save(
                Conversation(
                    title = "checkout latency",
                    investigationType =
                        InvestigationType.ALERT_INVESTIGATION,
                ),
            )

        val found = repository.findById(saved.id!!)

        assertThat(found).isNotNull
        assertThat(found!!.title).isEqualTo("checkout latency")
        assertThat(found.investigationType)
            .isEqualTo(InvestigationType.ALERT_INVESTIGATION)
    }

    @Test
    fun `returns null for a missing conversation`() {
        assertThat(repository.findById("missing")).isNull()
    }

    @Test
    fun `assigns an optimistic-lock version on first save`() {
        val saved =
            repository.save(
                Conversation(
                    title = "rollout health",
                    investigationType =
                        InvestigationType.ROLLOUT_HEALTH_CHECK,
                ),
            )

        assertThat(saved.id).isNotNull()
        assertThat(saved.version).isEqualTo(0L)
    }
}
