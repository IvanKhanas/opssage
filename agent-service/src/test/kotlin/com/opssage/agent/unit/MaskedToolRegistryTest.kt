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
package com.opssage.agent.unit

import com.opssage.agent.config.ToolPolicyProperties
import com.opssage.agent.masking.MaskedToolRegistry
import com.opssage.agent.masking.PiiMasker
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.ai.tool.definition.ToolDefinition

class MaskedToolRegistryTest {

    private val policy = ToolPolicyProperties()

    @Test
    fun `keeps configured read tools and drops write tools`() {
        val registry =
            registryOf(
                "searchFacts",
                "getRunbooksForService",
                "proposeInvestigationFact",
                "saveInvestigationSummary",
            )

        assertThat(registry.contains("searchFacts")).isTrue()
        assertThat(registry.contains("getRunbooksForService")).isTrue()
        assertThat(registry.contains("proposeInvestigationFact")).isFalse()
        assertThat(registry.contains("saveInvestigationSummary")).isFalse()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "searchFacts",
            "knowledge_searchFacts",
        ],
    )
    fun `keeps a read tool even when the client prefixes its name`(
        toolName: String,
    ) {
        val registry = registryOf(toolName)

        assertThat(registry.contains(toolName)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "proposeNewSkill",
            "knowledge_proposeNewSkill",
            "unknownClusterMutation",
        ],
    )
    fun `drops every tool that is not explicitly read allowed`(
        toolName: String,
    ) {
        val registry = registryOf(toolName)

        assertThat(registry.isEmpty()).isTrue()
    }

    private fun registryOf(vararg toolNames: String): MaskedToolRegistry {
        val provider =
            mockk<ToolCallbackProvider> {
                every { toolCallbacks } returns
                    toolNames.map(::toolCallback).toTypedArray()
            }
        return MaskedToolRegistry(listOf(provider), mockk<PiiMasker>(), policy)
    }

    private fun toolCallback(name: String): ToolCallback =
        mockk {
            every { toolDefinition } returns
                mockk<ToolDefinition> { every { name() } returns name }
        }
}
