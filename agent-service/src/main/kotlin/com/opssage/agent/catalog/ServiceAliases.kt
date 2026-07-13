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
package com.opssage.agent.catalog

object ServiceAliases {

    fun normalized(service: String): String =
        service
            .lowercase()
            .replace("_", "-")
            .trim('-')

    fun resolve(
        service: String,
        catalog: List<String>,
    ): String? {
        if (catalog.isEmpty()) {
            return service
        }
        catalog
            .firstOrNull { it.equals(service, ignoreCase = true) }
            ?.let { return it }
        val alias = normalized(service)
        return catalog.firstOrNull { key(it) == alias }
    }

    fun mentionedIn(
        text: String,
        catalog: List<String>,
    ): List<String> {
        val normalized = text.lowercase()
        return catalog.filter { service ->
            normalized.contains(service.lowercase())
        }
    }

    private fun key(service: String): String = normalized(service)
}
