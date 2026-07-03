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
package com.opssage.knowledge.mcp

import com.opssage.knowledge.config.PaginationProperties
import com.opssage.knowledge.model.KnownIncident
import com.opssage.knowledge.service.KnownIncidentService
import com.opssage.knowledge.util.blockingList
import com.opssage.knowledge.util.paged

import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component

@Component
class KnownIncidentMcpTools(
    private val knownIncidentService: KnownIncidentService,
    private val pagination: PaginationProperties,
) {

    @Tool(
        description =
            "Retrieve past known incidents recorded for a given service. " +
                "Use this to check whether the current symptoms match an " +
                "incident that has already been resolved before.",
    )
    fun getKnownIncidentsForService(
        serviceId: String,
        limit: Int?,
    ): List<KnownIncident> =
        knownIncidentService
            .findByService(serviceId)
            .paged(0, pagination.resolveSize(limit))
            .blockingList()

    @Tool(
        description =
            "Search known incidents by a keyword in their title. " +
                "Useful for finding historically similar outages.",
    )
    fun searchKnownIncidents(
        keyword: String,
        limit: Int?,
    ): List<KnownIncident> =
        knownIncidentService
            .searchByTitle(keyword)
            .paged(0, pagination.resolveSize(limit))
            .blockingList()

    @Tool(
        description =
            "Find known incidents where the given service was involved as a " +
                "related (upstream or downstream) service rather than the " +
                "primary one. Use this to trace cross-service impact.",
    )
    fun getIncidentsInvolvingService(
        serviceId: String,
        limit: Int?,
    ): List<KnownIncident> =
        knownIncidentService
            .findByRelatedService(serviceId)
            .paged(0, pagination.resolveSize(limit))
            .blockingList()
}
