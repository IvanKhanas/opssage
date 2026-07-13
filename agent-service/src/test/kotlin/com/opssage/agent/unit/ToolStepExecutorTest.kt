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

import com.opssage.agent.config.SreProperties
import com.opssage.agent.config.ToolExecutionRuntime
import com.opssage.agent.masking.MaskedToolRegistry
import com.opssage.agent.playbook.ToolStep
import com.opssage.agent.playbook.ToolStepExecutor
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tools.jackson.module.kotlin.jacksonObjectMapper

import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import kotlinx.coroutines.Dispatchers

@ExtendWith(MockKExtension::class)
class ToolStepExecutorTest {

    @MockK
    lateinit var registry: MaskedToolRegistry

    private val executor by lazy {
        ToolStepExecutor(
            registry,
            jacksonObjectMapper(),
            ToolExecutionRuntime(
                SreProperties("checkout", Duration.ofMinutes(5)),
                Dispatchers.Default,
            ),
        )
    }

    @Test
    fun `executes the steps in order and records their output`() {
        every { registry.call("getServiceHealth", any()) } returns "health"
        every { registry.call("findTopLogErrors", any()) } returns "errors"

        val observations =
            executor.execute(
                listOf(step("getServiceHealth"), step("findTopLogErrors")),
            )

        assertThat(observations.map { it.tool })
            .containsExactly("getServiceHealth", "findTopLogErrors")
        assertThat(observations.map { it.output })
            .containsExactly("health", "errors")
        assertThat(observations).allMatch { it.succeeded }
    }

    @Test
    fun `records a failed step as an observation and keeps going`() {
        every { registry.call("getServiceHealth", any()) } throws
            IllegalStateException("mcp down")
        every { registry.call("findTopLogErrors", any()) } returns "errors"

        val observations =
            executor.execute(
                listOf(step("getServiceHealth"), step("findTopLogErrors")),
            )

        assertThat(observations).hasSize(2)
        assertThat(observations[0].succeeded).isFalse()
        assertThat(observations[0].output).contains("недоступен")
        assertThat(observations[1].succeeded).isTrue()
        assertThat(observations[1].output).isEqualTo("errors")
    }

    @Test
    fun `never leaks the failure message of the tool into the observation`() {
        every { registry.call(any(), any()) } throws
            IllegalStateException("query=user@example.com rejected")

        val observations = executor.execute(listOf(step("findLogErrorsByText")))

        assertThat(observations.single().output)
            .doesNotContain("user@example.com")
    }

    @Test
    fun `serializes the step arguments as a JSON object`() {
        val arguments = slot<String>()
        every { registry.call(any(), capture(arguments)) } returns "health"

        executor.execute(
            listOf(
                ToolStep(
                    "getServiceHealth",
                    mapOf("service" to "checkout", "lookback" to "PT2H"),
                ),
            ),
        )

        assertThat(arguments.captured)
            .isEqualTo("""{"service":"checkout","lookback":"PT2H"}""")
    }

    @Test
    fun `concurrent investigations run their fan-outs without a shared cap`() {
        val width = 5
        val bothFanOuts = CyclicBarrier(2 * width)
        every { registry.call(any(), any()) } answers {
            bothFanOuts.await(5, TimeUnit.SECONDS)
            "ok"
        }
        val steps = (1..width).map { step("tool$it") }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first = pool.submit(Callable { executor.execute(steps) })
            val second = pool.submit(Callable { executor.execute(steps) })

            assertThat(first.get(10, TimeUnit.SECONDS))
                .allMatch { it.succeeded }
            assertThat(second.get(10, TimeUnit.SECONDS))
                .allMatch { it.succeeded }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun step(tool: String): ToolStep =
        ToolStep(
            tool,
            mapOf("service" to "checkout"),
        )
}
