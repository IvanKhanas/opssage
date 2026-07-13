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
package com.opssage.agent.playbook

import com.opssage.agent.config.ToolExecutionRuntime
import com.opssage.agent.masking.MaskedToolRegistry
import com.opssage.agent.model.Observation
import io.github.oshai.kotlinlogging.KotlinLogging
import tools.jackson.databind.ObjectMapper

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class ToolStepExecutor(
    private val registry: MaskedToolRegistry,
    private val mapper: ObjectMapper,
    private val runtime: ToolExecutionRuntime,
) {

    fun execute(steps: List<ToolStep>): List<Observation> {
        if (steps.isEmpty()) {
            return emptyList()
        }
        val investigationGate = Semaphore(runtime.toolConcurrency)
        return runBlocking {
            steps
                .map { step ->
                    async(runtime.dispatcher) {
                        investigationGate.withPermit {
                            runtime.globalGate.withPermit { observe(step) }
                        }
                    }
                }.awaitAll()
        }
    }

    private fun observe(step: ToolStep): Observation =
        try {
            Observation(
                tool = step.tool,
                output = registry.call(step.tool, arguments(step)),
                succeeded = true,
            )
        } catch (ex: RuntimeException) {
            log.atWarn {
                message = "Playbook step failed"
                payload = mapOf("tool" to step.tool)
                cause = ex
            }
            Observation(step.tool, UNAVAILABLE, succeeded = false)
        }

    private fun arguments(step: ToolStep): String =
        mapper.writeValueAsString(step.arguments)

    private companion object {
        const val UNAVAILABLE =
            "Инструмент недоступен, данные по этому шагу не собраны."
    }
}
