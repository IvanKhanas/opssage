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
package com.opssage.sre.util

import kotlin.math.roundToLong

object Numbers {

    const val PERCENT = 100.0

    private const val SCALE = 10_000.0

    private const val MILLIS_PER_SECOND = 1000.0

    private const val MICROS_PER_MILLI = 1000.0

    fun round(value: Double): Double = (value * SCALE).roundToLong() / SCALE

    fun millis(seconds: Double): Double =
        (seconds * MILLIS_PER_SECOND).roundToLong().toDouble()

    fun microsToMillis(micros: Long): Double = round(micros / MICROS_PER_MILLI)
}
