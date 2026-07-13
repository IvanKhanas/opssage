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
package com.opssage.agent.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("agent.tools")
data class ToolPolicyProperties(
    val readTools: List<String> = DEFAULT_READ_TOOLS,
) {

    fun isReadTool(toolName: String): Boolean =
        readTools.any { name ->
            toolName == name || toolName.endsWith("_$name")
        }

    private companion object {
        val DEFAULT_READ_TOOLS =
            listOf(
                "listServices",
                "getServiceHealth",
                "compareServiceBeforeAfterRollout",
                "getDependencyImpact",
                "findTopLogErrors",
                "findLogErrorsByText",
                "findServiceTraces",
                "findUserRelatedTraces",
                "summarizeTrace",
                "getServiceCorrectness",
                "getAlertContext",
                "getKubernetesServiceEvents",
                "readDocumentation",
                "validateTelemetryContract",
                "getFactsForService",
                "searchFacts",
                "getRunbooksForService",
                "getRunbooksByAlert",
                "getServiceProfile",
                "getKnownIncidentsForService",
                "searchKnownIncidents",
                "getIncidentsInvolvingService",
            )
    }
}
