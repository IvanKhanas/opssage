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

import com.opssage.agent.config.MaskingProperties
import com.opssage.agent.masking.MaskingToolCallback
import com.opssage.agent.masking.PiiMasker
import com.opssage.agent.masking.RegexPiiDetector
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.definition.ToolDefinition

class MaskingToolCallbackTest {

    private val properties = maskingProperties()
    private val masker =
        PiiMasker(properties, listOf(RegexPiiDetector(properties)))

    @Test
    fun `masks personal data in tool output before returning to the model`() {
        val delegate = mockk<ToolCallback>()
        every { delegate.call(any<String>()) } returns
            "error for admin@corp.io from 10.0.0.5"

        val masked = MaskingToolCallback(delegate, masker).call("{}")

        assertEquals("error for [EMAIL] from [IP]", masked)
    }

    @Test
    fun `passes tool input through unchanged to the delegate`() {
        val delegate = mockk<ToolCallback>()
        val captured = slot<String>()
        every { delegate.call(capture(captured)) } returns "ok"

        MaskingToolCallback(delegate, masker).call("service=payments")

        assertEquals("service=payments", captured.captured)
    }

    @Test
    fun `delegates tool definition to the wrapped callback`() {
        val delegate = mockk<ToolCallback>()
        val definition = mockk<ToolDefinition>()
        every { delegate.toolDefinition } returns definition

        val wrapped = MaskingToolCallback(delegate, masker)

        assertEquals(definition, wrapped.toolDefinition)
        verify { delegate.toolDefinition }
    }

    private fun maskingProperties(): MaskingProperties =
        MaskingProperties(
            enabled = true,
            emailToken = "[EMAIL]",
            ipToken = "[IP]",
            phoneToken = "[PHONE]",
            secretToken = "[REDACTED]",
            uuidToken = "[ID]",
            labelToken = "[PII]",
            nameToken = "[NAME]",
            maskFullNames = true,
            sensitiveLabels = listOf("userId", "firstName"),
            operationalIdPrefixes = emptyList(),
        )
}
