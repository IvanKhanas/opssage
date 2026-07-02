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

import com.opssage.knowledge.model.Fact
import com.opssage.knowledge.service.FactService
import com.opssage.knowledge.util.blockingList

import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component

@Component
class FactMcpTools(
    private val factService: FactService,
) {

    @Tool(
        description =
            "Retrieve all approved diagnostic facts for a given service. " +
                "Use this to check if similar incidents have been investigated before.",
    )
    fun getFactsForService(serviceId: String): List<Fact> =
        factService.findApprovedByService(serviceId).blockingList()

    @Tool(
        description =
            "Search approved facts by symptom keyword. " +
                "Useful for finding known patterns that match observed behavior.",
    )
    fun searchFacts(keyword: String): List<Fact> =
        factService.searchApprovedBySymptom(keyword).blockingList()

    @Tool(
        description =
            "Get approved facts matching a specific tag " +
                "such as 'latency', 'mongodb', 'oom'.",
    )
    fun getFactsByTag(tag: String): List<Fact> =
        factService.findApprovedByTag(tag).blockingList()
}
