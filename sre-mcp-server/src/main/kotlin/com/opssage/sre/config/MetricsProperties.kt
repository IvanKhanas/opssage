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
import jakarta.validation.constraints.NotBlank

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties("sre.victoria-metrics")
data class MetricsProperties(
    val baseUrl: String,
    val requestMetric: String,
    val requestBucketMetric: String,
    val serviceLabel: String,
    val namespaceLabel: String,
    @field:NotBlank
    val errorSelector: String,
    val maxServices: Int,
) {
    @AssertTrue(
        message =
            "sre.victoria-metrics.error-selector must be a label matcher " +
                "fragment such as outcome=\"SERVER_ERROR\" or code=~\"5..\", " +
                "without braces or line breaks",
    )
    fun hasContainedErrorSelector(): Boolean =
        FORBIDDEN.none(errorSelector::contains)

    private companion object {
        val FORBIDDEN = listOf("{", "}", "\n", "\r")
    }
}
