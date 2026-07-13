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

import com.opssage.agent.config.WindowProperties
import com.opssage.agent.investigation.AnchorWindowResolver
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class AnchorWindowResolverTest {

    private val resolver =
        AnchorWindowResolver(
            WindowProperties(
                defaultLookback = Duration.ofHours(2),
                maxLookback = Duration.ofHours(48),
                forwardBuffer = Duration.ofMinutes(5),
            ),
            Clock.fixed(NOW, ZoneOffset.UTC),
        )

    @Test
    fun `anchors the end at now plus the forward buffer by default`() {
        val window = resolver.resolve()

        assertThat(window.to).isEqualTo(NOW.plus(Duration.ofMinutes(5)))
        assertThat(window.from)
            .isEqualTo(window.to.minus(Duration.ofHours(2)))
    }

    @Test
    fun `applies the requested lookback from the anchor`() {
        val window = resolver.resolve(lookback = Duration.ofMinutes(30))

        assertThat(window.from)
            .isEqualTo(window.to.minus(Duration.ofMinutes(30)))
    }

    @Test
    fun `caps the lookback at the configured maximum`() {
        val window = resolver.resolve(lookback = Duration.ofDays(30))

        assertThat(window.from)
            .isEqualTo(window.to.minus(Duration.ofHours(48)))
    }

    @Test
    fun `caps an explicit span at the configured maximum`() {
        val to = NOW
        val from = to.minus(Duration.ofDays(30))

        val window = resolver.resolve(from = from, to = to)

        assertThat(window.to).isEqualTo(to)
        assertThat(window.from).isEqualTo(to.minus(Duration.ofHours(48)))
    }

    @Test
    fun `clamps a future end at now plus the forward buffer`() {
        val window = resolver.resolve(to = NOW.plus(Duration.ofDays(1)))

        assertThat(window.to).isEqualTo(NOW.plus(Duration.ofMinutes(5)))
    }

    @Test
    fun `keeps an explicit half-open window inside the bounds`() {
        val to = NOW.minus(Duration.ofHours(1))
        val from = to.minus(Duration.ofHours(3))

        val window = resolver.resolve(from = from, to = to)

        assertThat(window.from).isEqualTo(from)
        assertThat(window.to).isEqualTo(to)
    }

    @Test
    fun `rejects a window whose start is not before its end`() {
        assertThatThrownBy {
            resolver.resolve(from = NOW, to = NOW.minus(Duration.ofHours(1)))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-08T10:00:00Z")
    }
}
