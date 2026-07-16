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
package com.opssage.agent.unit

import com.opssage.agent.investigation.InvestigationReport
import com.opssage.agent.investigation.InvestigationRequest
import com.opssage.agent.investigation.InvestigationService
import com.opssage.agent.messaging.IdempotencyGuard
import com.opssage.agent.messaging.InvestigationCommandConsumer
import com.opssage.agent.messaging.InvestigationResultEvent
import com.opssage.agent.messaging.InvestigationResultPublisher
import com.opssage.agent.messaging.InvestigationResultStatus
import com.opssage.agent.model.Confidence
import com.opssage.agent.model.InvestigationType
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tools.jackson.module.kotlin.jacksonObjectMapper

import java.time.Duration
import java.time.Instant

@ExtendWith(MockKExtension::class)
class InvestigationCommandConsumerTest {

    @MockK
    lateinit var investigationService: InvestigationService

    @MockK
    lateinit var resultPublisher: InvestigationResultPublisher

    @MockK
    lateinit var idempotencyGuard: IdempotencyGuard

    private val request = slot<InvestigationRequest>()
    private val result = slot<InvestigationResultEvent>()

    @Test
    fun `consumes kafka command and starts investigation`() {
        every { investigationService.investigate(capture(request)) } returns
            InvestigationReport(
                conversationId = "conv-1",
                investigationType = InvestigationType.ALERT_INVESTIGATION,
                summary = "deposit degraded",
                confidence = Confidence.MEDIUM,
                evidence = emptyList(),
            )
        justRun { resultPublisher.publish(capture(result)) }
        every { idempotencyGuard.tryStart(any()) } returns true
        val consumer =
            InvestigationCommandConsumer(
                investigationService,
                resultPublisher,
                jacksonObjectMapper(),
                idempotencyGuard,
            )

        consumer.consume(commandJson())

        assertThat(request.captured.title).isEqualTo("deposit alert")
        assertThat(request.captured.investigationType)
            .isEqualTo(InvestigationType.ALERT_INVESTIGATION)
        assertThat(request.captured.input).isEqualTo("HighErrorRate fired")
        assertThat(request.captured.lookback).isEqualTo(Duration.ofHours(4))
        assertThat(request.captured.from)
            .isEqualTo(Instant.parse("2026-07-13T10:00:00Z"))
        assertThat(result.captured.requestId).isEqualTo("req-1")
        assertThat(result.captured.status)
            .isEqualTo(InvestigationResultStatus.COMPLETED)
        verify { resultPublisher.publish(any()) }
    }

    @Test
    fun `publishes failed result when investigation fails`() {
        every { investigationService.investigate(any()) } throws
            IllegalStateException("llm unavailable")
        justRun { resultPublisher.publish(capture(result)) }
        every { idempotencyGuard.tryStart(any()) } returns true
        val consumer =
            InvestigationCommandConsumer(
                investigationService,
                resultPublisher,
                jacksonObjectMapper(),
                idempotencyGuard,
            )

        assertThatCode { consumer.consume(commandJson()) }
            .doesNotThrowAnyException()

        assertThat(result.captured.requestId).isEqualTo("req-1")
        assertThat(result.captured.status)
            .isEqualTo(InvestigationResultStatus.FAILED)
        verify { resultPublisher.publish(any()) }
    }

    @Test
    fun `skips already-processed command without re-running investigation`() {
        every { idempotencyGuard.tryStart(any()) } returns false
        val consumer =
            InvestigationCommandConsumer(
                investigationService,
                resultPublisher,
                jacksonObjectMapper(),
                idempotencyGuard,
            )

        consumer.consume(commandJson())

        verify(exactly = 0) { investigationService.investigate(any()) }
        verify(exactly = 0) { resultPublisher.publish(any()) }
    }

    @Test
    fun `throws malformed command so kafka error handler can route dlq`() {
        val consumer =
            InvestigationCommandConsumer(
                investigationService,
                resultPublisher,
                jacksonObjectMapper(),
                idempotencyGuard,
            )

        assertThatCode { consumer.consume("{") }
            .isInstanceOf(Exception::class.java)
    }

    private fun commandJson(): String =
        """
        {
          "metadata": {
            "requestId": "req-1",
            "requestedAt": "2026-07-13T12:00:00Z"
          },
          "request": {
            "title": "deposit alert",
            "investigationType": "ALERT_INVESTIGATION",
            "input": "HighErrorRate fired"
          },
          "window": {
            "from": "2026-07-13T10:00:00Z",
            "lookback": "PT4H"
          }
        }
        """.trimIndent()
}
