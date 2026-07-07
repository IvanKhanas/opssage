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
package com.opssage.sre.unit

import com.opssage.sre.client.VictoriaLogsClient
import com.opssage.sre.config.LogsProperties
import com.opssage.sre.logs.LogErrorAggregator
import com.opssage.sre.logs.LogQuery
import com.opssage.sre.logs.LogsService
import com.opssage.sre.model.Confidence
import com.opssage.sre.model.LogRecord
import com.opssage.sre.time.TimeWindow
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono

import java.time.Instant

@ExtendWith(MockKExtension::class)
class LogsServiceTest {

    @MockK
    private lateinit var client: VictoriaLogsClient

    private val logs =
        LogsProperties(
            baseUrl = "http://vl",
            serviceField = "service",
            namespaceField = "namespace",
            levelField = "level",
            errorLevel = "ERROR",
            messageField = "_msg",
            traceField = "trace_id",
            timeField = "_time",
            maxSamples = 500,
            maxScanSamples = 5_000,
        )
    private val window =
        TimeWindow(
            Instant.parse("2026-06-27T10:00:00Z"),
            Instant.parse("2026-06-27T11:00:00Z"),
        )
    private lateinit var service: LogsService

    @BeforeEach
    fun setUp() {
        service = LogsService(client, LogErrorAggregator(), logs)
    }

    @Test
    fun `groups error logs and reports high confidence`() {
        val records =
            listOf(
                LogRecord("Timeout after 10ms", "t1", "2026-06-27T10:01:00Z"),
                LogRecord("Timeout after 20ms", "t2", "2026-06-27T10:02:00Z"),
            )
        every { client.errorLogs(any(), any(), any()) } returns
            Mono.just(records)

        val result =
            service
                .topLogErrors(
                    LogQuery("deposit-service", "banking", 10),
                    window,
                ).block()!!

        assertThat(result.topErrors).hasSize(1)
        assertThat(result.topErrors[0].count).isEqualTo(2)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun `reports medium confidence when the sample limit is reached`() {
        val truncated =
            LogsService(
                client,
                LogErrorAggregator(),
                logs.copy(maxScanSamples = 2),
            )
        val records =
            listOf(
                LogRecord("Timeout after 10ms", "t1", "2026-06-27T10:01:00Z"),
                LogRecord("Connection reset", "t2", "2026-06-27T10:02:00Z"),
            )
        every { client.errorLogs(any(), any(), any()) } returns
            Mono.just(records)

        val result =
            truncated
                .topLogErrors(
                    LogQuery("deposit-service", "banking", 10),
                    window,
                ).block()!!

        assertThat(result.confidence).isEqualTo(Confidence.MEDIUM)
    }

    @Test
    fun `reports low confidence when no error logs are found`() {
        every { client.errorLogs(any(), any(), any()) } returns
            Mono.just(emptyList())

        val result =
            service
                .topLogErrors(
                    LogQuery("deposit-service", "banking", 10),
                    window,
                ).block()!!

        assertThat(result.topErrors).isEmpty()
        assertThat(result.confidence).isEqualTo(Confidence.LOW)
    }
}
