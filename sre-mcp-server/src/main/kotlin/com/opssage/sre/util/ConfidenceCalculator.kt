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

import com.opssage.sre.model.Confidence

object ConfidenceCalculator {

    fun of(
        withData: Int,
        total: Int,
    ): Confidence =
        when {
            total == 0 || withData == 0 -> Confidence.LOW
            withData == total -> Confidence.HIGH
            else -> Confidence.MEDIUM
        }

    fun ofSamples(
        sampleCount: Int,
        maxSamples: Int,
    ): Confidence =
        when {
            sampleCount == 0 -> Confidence.LOW
            sampleCount >= maxSamples -> Confidence.MEDIUM
            else -> Confidence.HIGH
        }

    fun lowest(values: List<Confidence>): Confidence =
        values.minByOrNull { it.ordinal } ?: Confidence.LOW
}
