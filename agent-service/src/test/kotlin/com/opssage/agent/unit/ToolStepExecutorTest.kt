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
import com.opssage.agent.model.Observation
import com.opssage.agent.playbook.ToolStep
import com.opssage.agent.playbook.ToolStepExecutor
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tools.jackson.module.kotlin.jacksonObjectMapper

import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

@ExtendWith(MockKExtension::class)
class ToolStepExecutorTest {

    @MockK
    lateinit var registry: MaskedToolRegistry

    private val dispatcher: ExecutorCoroutineDispatcher =
        Executors
            .newFixedThreadPool(TEST_DISPATCHER_THREADS)
            .asCoroutineDispatcher()

    private val executor by lazy {
        ToolStepExecutor(
            registry,
            jacksonObjectMapper(),
            ToolExecutionRuntime(
                SreProperties("checkout", Duration.ofMinutes(5)),
                dispatcher,
            ),
        )
    }

    @AfterEach
    fun closeDispatcher() {
        dispatcher.close()
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
        val width = 2
        val expectedConcurrentCalls = 2 * width
        val started = CountDownLatch(expectedConcurrentCalls)
        val release = CountDownLatch(1)
        val active = AtomicInteger()
        every { registry.call(any(), any()) } answers {
            active.incrementAndGet()
            started.countDown()
            release.await(10, TimeUnit.SECONDS)
            active.decrementAndGet()
            "ok"
        }
        val steps = (1..width).map { step("tool$it") }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first = pool.submit(Callable { executor.execute(steps) })
            val second = pool.submit(Callable { executor.execute(steps) })

            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue()
            assertThat(active.get()).isEqualTo(expectedConcurrentCalls)

            release.countDown()
            assertSucceeded(first)
            assertSucceeded(second)
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    private fun assertSucceeded(result: Future<List<Observation>>) {
        assertThat(result.get(10, TimeUnit.SECONDS))
            .allMatch { it.succeeded }
    }

    private fun step(tool: String): ToolStep =
        ToolStep(
            tool,
            mapOf("service" to "checkout"),
        )

    private companion object {
        const val TEST_DISPATCHER_THREADS = 8
    }
}
