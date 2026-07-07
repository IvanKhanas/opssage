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
import com.opssage.sre.logs.LogErrorAggregator
import com.opssage.sre.metrics.RolloutErrorDiff
import com.opssage.sre.model.LogRecord
import com.opssage.sre.time.TimeWindow
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono

import java.time.Instant

@ExtendWith(MockKExtension::class)
class RolloutErrorDiffTest {

    @MockK
    private lateinit var client: VictoriaLogsClient

    private val before =
        TimeWindow(
            Instant.parse("2026-06-27T10:00:00Z"),
            Instant.parse("2026-06-27T10:30:00Z"),
        )
    private val after =
        TimeWindow(
            Instant.parse("2026-06-27T10:30:00Z"),
            Instant.parse("2026-06-27T11:00:00Z"),
        )

    @Test
    fun `returns only fingerprints that appear after the deploy`() {
        every { client.errorLogs("deposit-service", "banking", before) } returns
            Mono.just(
                listOf(
                    LogRecord("Timeout after 10ms", "t1", "10:01:00Z"),
                ),
            )
        every { client.errorLogs("deposit-service", "banking", after) } returns
            Mono.just(
                listOf(
                    LogRecord("Timeout after 30ms", "t2", "10:31:00Z"),
                    LogRecord("NullPointer at line 5", "t3", "10:32:00Z"),
                    LogRecord("NullPointer at line 9", "t4", "10:33:00Z"),
                ),
            )
        val diff = RolloutErrorDiff(client, LogErrorAggregator())

        val result =
            diff
                .newErrors("deposit-service", "banking", before, after, 20)
                .block()!!

        assertThat(result).hasSize(1)
        assertThat(result[0].fingerprint).isEqualTo("NullPointer at line <num>")
        assertThat(result[0].count).isEqualTo(2)
    }
}
