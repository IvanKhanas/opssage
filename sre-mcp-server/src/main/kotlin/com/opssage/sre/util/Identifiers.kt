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

object Identifiers {

    private const val MAX_LENGTH = 253

    private val VALID = Regex("[A-Za-z0-9_.:-]+")

    fun require(
        field: String,
        value: String,
    ): String {
        require(value.length in 1..MAX_LENGTH && VALID.matches(value)) {
            "Invalid $field '$value': expected 1..$MAX_LENGTH characters " +
                "from [A-Za-z0-9_.:-]"
        }
        return value
    }

    fun requireValue(
        field: String,
        value: String,
    ): String {
        require(value.length in 1..MAX_LENGTH) {
            "Invalid $field: expected 1..$MAX_LENGTH characters"
        }
        return value
    }
}
