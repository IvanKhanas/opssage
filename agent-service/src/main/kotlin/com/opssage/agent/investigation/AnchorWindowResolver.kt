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
package com.opssage.agent.investigation

import com.opssage.agent.config.WindowProperties
import com.opssage.agent.model.AnchorWindow

import java.time.Clock
import java.time.Duration
import java.time.Instant

import org.springframework.stereotype.Component

@Component
class AnchorWindowResolver(
    private val properties: WindowProperties,
    private val clock: Clock,
) {

    fun resolve(
        from: Instant? = null,
        to: Instant? = null,
        lookback: Duration? = null,
    ): AnchorWindow {
        val forwardBoundary = clock.instant().plus(properties.forwardBuffer)
        val anchor = minOf(to ?: forwardBoundary, forwardBoundary)

        val effectiveLookback =
            minOf(
                lookback ?: properties.defaultLookback,
                properties.maxLookback,
            )
        val requestedStart = from ?: anchor.minus(effectiveLookback)
        val start =
            if (Duration.between(requestedStart, anchor) >
                properties.maxLookback
            ) {
                anchor.minus(properties.maxLookback)
            } else {
                requestedStart
            }

        require(start.isBefore(anchor)) {
            "Investigation window start must be strictly before its end"
        }
        return AnchorWindow(from = start, to = anchor)
    }
}
