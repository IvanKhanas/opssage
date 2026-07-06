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

import java.time.Duration
import java.time.Instant

data class TimeWindow(
    val from: Instant,
    val to: Instant,
) {

    val duration: Duration get() = Duration.between(from, to)

    fun stepSeconds(maxPoints: Int): Long {
        val span = duration.seconds
        val points = maxOf(maxPoints, 1)
        return maxOf(span / points, 1)
    }
}
