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
package com.opssage.sre.documentation

import java.net.URI

object DocumentationTitle {

    private const val HEADING_PREFIX = "# "

    fun of(
        content: String,
        link: String,
    ): String = firstHeading(content) ?: fallback(link)

    private fun firstHeading(content: String): String? =
        content
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith(HEADING_PREFIX) }
            ?.removePrefix(HEADING_PREFIX)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun fallback(link: String): String {
        val path = URI(link).path.orEmpty()
        return path
            .trimEnd('/')
            .substringAfterLast('/')
            .ifBlank { link }
    }
}
