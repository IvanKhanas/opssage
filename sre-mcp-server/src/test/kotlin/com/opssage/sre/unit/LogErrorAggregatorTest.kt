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

import com.opssage.sre.logs.LogErrorAggregator
import com.opssage.sre.model.LogRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LogErrorAggregatorTest {

    private val aggregator = LogErrorAggregator()

    @Test
    fun `groups messages that differ only in variable parts`() {
        val records =
            listOf(
                record(
                    "Timeout calling core-banking-adapter after 5000ms",
                    "t1",
                ),
                record(
                    "Timeout calling core-banking-adapter after 3200ms",
                    "t2",
                ),
                record("NullPointerException at Foo.bar", null),
            )

        val top = aggregator.aggregate(records, 10)

        assertThat(top).hasSize(2)
        assertThat(top[0].count).isEqualTo(2)
        assertThat(top[0].fingerprint).contains("<num>")
        assertThat(top[0].sampleTraceIds).containsExactly("t1", "t2")
        assertThat(top[0].firstSeen).isEqualTo("2026-06-27T10:00:00Z")
        assertThat(top[0].lastSeen).isEqualTo("2026-06-27T10:05:00Z")
    }

    @Test
    fun `normalises uuids in the fingerprint`() {
        val fingerprint =
            aggregator.fingerprint(
                "Order 550e8400-e29b-41d4-a716-446655440000 failed",
            )

        assertThat(fingerprint).isEqualTo("Order <uuid> failed")
    }

    @Test
    fun `caps the number of returned fingerprints to the limit`() {
        val records =
            listOf(
                record("error alpha", null),
                record("error beta", null),
                record("error gamma", null),
            )

        val top = aggregator.aggregate(records, 2)

        assertThat(top).hasSize(2)
    }

    private var tick = 0

    private fun record(
        message: String,
        traceId: String?,
    ): LogRecord {
        val time = "2026-06-27T10:0$tick:00Z"
        tick += 5
        return LogRecord(message, traceId, time)
    }
}
