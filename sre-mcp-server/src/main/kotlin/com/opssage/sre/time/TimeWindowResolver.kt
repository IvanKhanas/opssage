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
package com.opssage.sre.time

import com.opssage.sre.config.QueryProperties
import com.opssage.sre.util.ToolInputs

import java.time.Clock
import java.time.Duration
import java.time.Instant

import org.springframework.stereotype.Component

@Component
class TimeWindowResolver(
    private val query: QueryProperties,
    private val clock: Clock,
) {

    fun fromLookback(lookback: String?): TimeWindow =
        resolve(
            TimeWindowRequest(
                lookback =
                    lookback?.let {
                        ToolInputs.duration(
                            "lookback",
                            it,
                        )
                    },
            ),
        )

    fun resolve(request: TimeWindowRequest): TimeWindow {
        val now = Instant.now(clock)
        val anchor = request.anchorTime
        val to = request.to ?: bufferedEnd(anchor, now)
        val lookback = request.lookback ?: query.defaultLookback
        val from = request.from ?: (anchor ?: to).minus(lookback)
        return clamp(from, to)
    }

    private fun bufferedEnd(
        anchor: Instant?,
        now: Instant,
    ): Instant {
        if (anchor == null) return now
        val buffered = anchor.plus(query.forwardBuffer)
        return if (buffered.isBefore(now)) buffered else now
    }

    private fun clamp(
        from: Instant,
        to: Instant,
    ): TimeWindow {
        val maxWindow = query.maxWindow
        val safeFrom =
            if (!from.isBefore(to)) {
                to.minus(query.defaultLookback)
            } else if (Duration.between(from, to) > maxWindow) {
                to.minus(maxWindow)
            } else {
                from
            }
        return TimeWindow(safeFrom, to)
    }
}
