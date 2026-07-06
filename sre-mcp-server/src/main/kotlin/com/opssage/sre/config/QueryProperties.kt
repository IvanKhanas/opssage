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
package com.opssage.sre.config

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

import java.time.Duration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties("sre.query")
data class QueryProperties(
    val defaultLookback: Duration,
    val maxWindow: Duration,
    val forwardBuffer: Duration,
    @field:Min(1)
    @field:Max(10_000)
    val maxPoints: Int,
    @field:Min(1)
    @field:Max(500)
    val maxNewErrors: Int,
    @field:Min(1)
    @field:Max(500)
    val maxTraces: Int,
    @field:Min(1)
    @field:Max(5_000)
    val maxSpans: Int,
    @field:Min(1)
    @field:Max(500)
    val maxPods: Int,
    @field:Min(1)
    @field:Max(1_000)
    val maxEvents: Int,
    @field:Min(1)
    @field:Max(200)
    val maxDependencies: Int,
    @field:Min(1)
    @field:Max(100)
    val alertLogErrors: Int,
) {
    @AssertTrue
    fun hasPositiveDefaultLookback(): Boolean =
        !defaultLookback.isZero && !defaultLookback.isNegative

    @AssertTrue
    fun hasPositiveMaxWindow(): Boolean =
        !maxWindow.isZero && !maxWindow.isNegative

    @AssertTrue
    fun hasPositiveForwardBuffer(): Boolean = !forwardBuffer.isNegative
}
