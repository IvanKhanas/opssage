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

import com.opssage.sre.config.QueryProperties
import com.opssage.sre.time.TimeWindowRequest
import com.opssage.sre.time.TimeWindowResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class TimeWindowResolverTest {

    private val now = Instant.parse("2026-06-27T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val query =
        QueryProperties(
            defaultLookback = Duration.ofHours(2),
            maxWindow = Duration.ofHours(48),
            forwardBuffer = Duration.ofMinutes(10),
            maxPoints = 120,
            maxNewErrors = 20,
            maxTraces = 20,
            maxSpans = 200,
            maxPods = 50,
            maxEvents = 50,
            maxDependencies = 20,
            alertLogErrors = 5,
        )
    private val resolver = TimeWindowResolver(query, clock)

    @Test
    fun `defaults to server now minus default lookback`() {
        val window = resolver.resolve(TimeWindowRequest())

        assertThat(window.to).isEqualTo(now)
        assertThat(window.from).isEqualTo(now.minus(Duration.ofHours(2)))
    }

    @Test
    fun `anchors lookback behind and extends past by the forward buffer`() {
        val anchor = Instant.parse("2026-06-27T10:30:00Z")
        val window =
            resolver.resolve(
                TimeWindowRequest(
                    lookback = Duration.ofMinutes(30),
                    anchorTime = anchor,
                ),
            )

        assertThat(window.from).isEqualTo(anchor.minus(Duration.ofMinutes(30)))
        assertThat(window.to).isEqualTo(anchor.plus(Duration.ofMinutes(10)))
    }

    @Test
    fun `never extends the buffered end past now`() {
        val anchor = now.minus(Duration.ofMinutes(5))
        val window = resolver.resolve(TimeWindowRequest(anchorTime = anchor))

        assertThat(window.to).isEqualTo(now)
        assertThat(window.from).isEqualTo(anchor.minus(Duration.ofHours(2)))
    }

    @Test
    fun `respects explicit absolute from and to`() {
        val from = Instant.parse("2026-06-27T09:00:00Z")
        val to = Instant.parse("2026-06-27T10:00:00Z")

        val window = resolver.resolve(TimeWindowRequest(from = from, to = to))

        assertThat(window.from).isEqualTo(from)
        assertThat(window.to).isEqualTo(to)
    }

    @Test
    fun `clamps a span wider than the maximum window`() {
        val from = Instant.parse("2026-06-24T00:00:00Z")
        val to = Instant.parse("2026-06-27T00:00:00Z")

        val window = resolver.resolve(TimeWindowRequest(from = from, to = to))

        assertThat(window.to).isEqualTo(to)
        assertThat(window.from).isEqualTo(to.minus(Duration.ofHours(48)))
    }

    @Test
    fun `falls back to default lookback when from is after to`() {
        val from = Instant.parse("2026-06-27T11:00:00Z")
        val to = Instant.parse("2026-06-27T10:00:00Z")

        val window = resolver.resolve(TimeWindowRequest(from = from, to = to))

        assertThat(window.from).isEqualTo(to.minus(Duration.ofHours(2)))
        assertThat(window.to).isEqualTo(to)
    }
}
