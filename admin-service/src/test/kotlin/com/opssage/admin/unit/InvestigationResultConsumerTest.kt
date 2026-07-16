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
package com.opssage.admin.unit

import com.opssage.admin.messaging.InvestigationResultConsumer
import com.opssage.admin.messaging.InvestigationResultEvent
import com.opssage.admin.model.InvestigationRequestStatus
import com.opssage.admin.service.InvestigationRequestStore
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tools.jackson.module.kotlin.jacksonObjectMapper

@ExtendWith(MockKExtension::class)
class InvestigationResultConsumerTest {

    @MockK
    lateinit var store: InvestigationRequestStore

    private val event = slot<InvestigationResultEvent>()

    @Test
    fun `consumes result event and updates store`() {
        justRun { store.complete(capture(event)) }
        val consumer = InvestigationResultConsumer(store, jacksonObjectMapper())

        consumer.consume(
            """
            {
              "requestId": "req-1",
              "status": "FAILED"
            }
            """.trimIndent(),
        )

        assertThat(event.captured.requestId).isEqualTo("req-1")
        assertThat(event.captured.status)
            .isEqualTo(InvestigationRequestStatus.FAILED)
        verify { store.complete(any()) }
    }
}
