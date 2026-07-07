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

import java.net.URI
import java.net.URISyntaxException

object Urls {

    private val ALLOWED_SCHEMES = setOf("http", "https")

    fun requireHttpUrl(
        field: String,
        value: String,
    ): String {
        val uri =
            try {
                URI(value)
            } catch (error: URISyntaxException) {
                throw IllegalArgumentException(
                    "Invalid $field '$value': expected an absolute http(s) URL",
                    error,
                )
            }
        require(
            uri.scheme?.lowercase() in ALLOWED_SCHEMES &&
                !uri.host.isNullOrBlank(),
        ) {
            "Invalid $field '$value': expected an absolute http(s) URL"
        }
        return value
    }
}
