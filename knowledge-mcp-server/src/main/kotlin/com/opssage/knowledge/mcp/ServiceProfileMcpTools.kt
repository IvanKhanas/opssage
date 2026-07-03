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

import com.opssage.knowledge.model.ServiceProfile
import com.opssage.knowledge.service.ServiceProfileService
import com.opssage.knowledge.util.blockingGet

import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component

@Component
class ServiceProfileMcpTools(
    private val serviceProfileService: ServiceProfileService,
) {

    @Tool(
        description =
            "Get the profile of a service: team ownership, criticality, " +
                "upstream and downstream services, Kafka topics, important " +
                "endpoints, common failure modes, metric hints and on-call " +
                "contacts. Use it to understand a service's dependencies and " +
                "typical failures before investigating.",
    )
    fun getServiceProfile(serviceId: String): ServiceProfile? =
        serviceProfileService.findByServiceId(serviceId).blockingGet()
}
