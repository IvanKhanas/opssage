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

import com.opssage.knowledge.config.McpProperties
import com.opssage.knowledge.config.PaginationProperties
import com.opssage.knowledge.model.Runbook
import com.opssage.knowledge.service.RunbookService
import com.opssage.knowledge.util.blockingList
import com.opssage.knowledge.util.paged

import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component

@Component
class RunbookMcpTools(
    private val runbookService: RunbookService,
    private val pagination: PaginationProperties,
    private val mcp: McpProperties,
) {

    @Tool(
        description =
            "Retrieve all runbooks for a given service. Each runbook " +
                "lists its trigger type, symptoms, ordered steps, " +
                "recommended tools and danger notes. Use this to find " +
                "response procedures relevant to the current incident.",
    )
    fun getRunbooksForService(
        serviceId: String,
        limit: Int?,
    ): List<Runbook> =
        runbookService
            .findByService(serviceId)
            .paged(0, pagination.resolveSize(limit))
            .blockingList(mcp.callTimeout)

    @Tool(
        description =
            "Find runbooks associated with a specific alert name " +
                "from vmalert or Alertmanager.",
    )
    fun getRunbooksByAlert(
        alertName: String,
        limit: Int?,
    ): List<Runbook> =
        runbookService
            .findByAlert(alertName)
            .paged(0, pagination.resolveSize(limit))
            .blockingList(mcp.callTimeout)
}
