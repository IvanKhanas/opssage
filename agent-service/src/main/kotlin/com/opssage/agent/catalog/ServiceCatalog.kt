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

import com.opssage.agent.config.SreProperties
import com.opssage.agent.masking.MaskedToolRegistry
import com.opssage.agent.tools.ToolOutputReader
import io.github.oshai.kotlinlogging.KotlinLogging
import tools.jackson.databind.JsonNode

import java.time.Clock
import java.time.Instant

import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

private data class CachedCatalog(
    val services: List<String>,
    val expiresAt: Instant,
)

@Component
class ServiceCatalog(
    private val registry: MaskedToolRegistry,
    private val reader: ToolOutputReader,
    private val properties: SreProperties,
    private val clock: Clock,
) {

    @Volatile
    private var cached: CachedCatalog? = null

    fun services(): List<String> {
        val now = clock.instant()
        cached
            ?.takeIf { it.expiresAt.isAfter(now) }
            ?.let { return it.services }

        val fresh = load()
        if (fresh.isNotEmpty()) {
            cached = CachedCatalog(fresh, now.plus(properties.catalogTtl))
        }
        return fresh
    }

    private fun load(): List<String> {
        if (!registry.contains(LIST_SERVICES)) {
            log.atWarn {
                message =
                    "Service catalog tool is not exposed by the MCP server"
            }
            return emptyList()
        }
        return try {
            parse(registry.call(LIST_SERVICES, NO_ARGUMENTS))
        } catch (ex: RuntimeException) {
            log.atWarn {
                message = "Failed to read the service catalog"
                cause = ex
            }
            emptyList()
        }
    }

    private fun parse(output: String): List<String> {
        val root =
            reader.read(output)
                ?: throw IllegalArgumentException(
                    "Service catalog output has an unsupported shape",
                )
        val services =
            reader
                .array(root, SERVICES_FIELD)
                .filter(JsonNode::isString)
                .map(JsonNode::asString)
        log.atInfo {
            message = "Loaded service catalog"
            payload = mapOf("count" to services.size)
        }
        return services
    }

    private companion object {
        const val LIST_SERVICES = "listServices"
        const val NO_ARGUMENTS = "{}"
        const val SERVICES_FIELD = "services"
    }
}
