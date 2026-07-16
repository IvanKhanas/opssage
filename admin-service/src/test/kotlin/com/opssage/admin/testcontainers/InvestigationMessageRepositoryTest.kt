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
package com.opssage.admin.testcontainers

import com.opssage.admin.model.InvestigationMessageCommand
import com.opssage.admin.model.InvestigationMessageMetadata
import com.opssage.admin.model.InvestigationMessagePayload
import com.opssage.admin.model.InvestigationMessageRecord
import com.opssage.admin.model.InvestigationMessageState
import com.opssage.admin.model.InvestigationRequestOwner
import com.opssage.admin.model.InvestigationRequestStatus
import com.opssage.admin.model.InvestigationRequestTimestamps
import com.opssage.admin.repository.InvestigationMessageRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "admin.auth.jwt.secret=12345678901234567890123456789012",
        "spring.kafka.listener.auto-startup=false",
    ],
)
class InvestigationMessageRepositoryTest {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun mongoProps(registry: DynamicPropertyRegistry) =
            TestContainersConfiguration.configure(registry)
    }

    @Autowired
    lateinit var repository: InvestigationMessageRepository

    @Autowired
    lateinit var mongo: MongoTemplate

    @BeforeEach
    fun cleanUp() {
        mongo.dropCollection(InvestigationMessageRecord::class.java)
    }

    @Test
    fun `finds messages by request id and owner user id`() {
        repository.save(record("msg-1", "req-1", "user-1"))
        repository.save(record("msg-2", "req-1", "user-2"))

        val result =
            repository.findByRequestIdAndUserId(
                "req-1",
                "user-1",
                PageRequest.of(0, 10),
            )

        assertThat(result.map { it.command.metadata.messageId })
            .containsExactly("msg-1")
    }

    @Test
    fun `does not find another user's message by message id`() {
        repository.save(record("msg-1", "req-1", "user-1"))

        val result = repository.findByMessageIdAndUserId("msg-1", "user-2")

        assertThat(result).isNull()
    }

    private fun record(
        messageId: String,
        requestId: String,
        userId: String,
    ): InvestigationMessageRecord =
        InvestigationMessageRecord(
            command =
                InvestigationMessageCommand(
                    metadata =
                        InvestigationMessageMetadata(
                            messageId = messageId,
                            requestId = requestId,
                        ),
                    owner =
                        InvestigationRequestOwner(
                            userId = userId,
                            roles = setOf("SRE"),
                        ),
                    payload =
                        InvestigationMessagePayload(
                            conversationId = "conv-$messageId",
                            input = "details",
                        ),
                ),
            state =
                InvestigationMessageState(
                    status = InvestigationRequestStatus.ACCEPTED,
                    timestamps = InvestigationRequestTimestamps(),
                ),
        )
}
