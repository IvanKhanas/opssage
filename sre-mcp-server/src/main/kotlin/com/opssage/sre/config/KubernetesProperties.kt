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

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("sre.kubernetes")
data class KubernetesProperties(
    val baseUrl: String,
    val token: String,
    val tokenPath: String?,
    val appLabels: List<String>,
    val caCertPath: String?,
) {
    fun podSelectors(): List<String> =
        appLabels.filter(String::isNotBlank).ifEmpty { DEFAULT_LABELS }

    override fun toString(): String =
        "KubernetesProperties(baseUrl=$baseUrl, " +
            "appLabels=$appLabels, token=<redacted>, " +
            "tokenPath=$tokenPath, caCertPath=$caCertPath)"

    private companion object {
        val DEFAULT_LABELS = listOf("app")
    }
}
