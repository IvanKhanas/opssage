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

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties("sre.victoria-logs")
data class LogsProperties(
    val baseUrl: String,
    val serviceField: String,
    val namespaceField: String,
    val levelField: String,
    val errorLevels: List<String>,
    val messageField: String,
    val traceField: String,
    val timeField: String,
    @field:Min(1)
    @field:Max(10_000)
    val maxSamples: Int,
    @field:Min(1)
    @field:Max(100_000)
    val maxScanSamples: Int,
) {
    @AssertTrue
    fun hasValidScanLimit(): Boolean = maxScanSamples >= maxSamples

    @AssertTrue(
        message =
            "sre.victoria-logs.error-levels must list at least one level, " +
                "for example ERROR,error,FATAL",
    )
    fun hasErrorLevels(): Boolean = errorLevels.any(String::isNotBlank)
}
