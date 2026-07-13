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

import com.opssage.agent.model.InvestigationType

object Playbooks {

    private val BY_TYPE: Map<InvestigationType, Playbook> =
        mapOf(
            InvestigationType.USER_PROBLEM_INVESTIGATION to
                Playbook { context ->
                    listOfNotNull(
                        windowed(SreTools.GET_SERVICE_HEALTH, context),
                        textSearch(context),
                        userTraces(context),
                        windowed(SreTools.FIND_TOP_LOG_ERRORS, context),
                        windowed(SreTools.GET_SERVICE_CORRECTNESS, context),
                    )
                },
            InvestigationType.ROLLOUT_HEALTH_CHECK to
                Playbook { context ->
                    listOf(
                        windowed(SreTools.GET_SERVICE_HEALTH, context),
                        scoped(
                            SreTools.GET_KUBERNETES_SERVICE_EVENTS,
                            context,
                        ),
                        windowed(SreTools.FIND_TOP_LOG_ERRORS, context),
                    )
                },
            InvestigationType.ALERT_INVESTIGATION to
                Playbook { context ->
                    listOf(
                        windowed(SreTools.GET_ALERT_CONTEXT, context),
                        windowed(SreTools.FIND_TOP_LOG_ERRORS, context),
                        windowed(SreTools.GET_SERVICE_CORRECTNESS, context),
                        windowed(SreTools.FIND_SERVICE_TRACES, context),
                    )
                },
            InvestigationType.ANALYTICAL_REQUEST to
                Playbook { context ->
                    listOfNotNull(
                        windowed(SreTools.GET_SERVICE_HEALTH, context),
                        windowed(SreTools.FIND_TOP_LOG_ERRORS, context),
                        textSearch(context),
                        windowed(SreTools.GET_SERVICE_CORRECTNESS, context),
                        windowed(SreTools.FIND_SERVICE_TRACES, context),
                    )
                },
            InvestigationType.GENERAL_SERVICE_INVESTIGATION to
                Playbook { context ->
                    listOf(
                        windowed(SreTools.GET_SERVICE_HEALTH, context),
                        windowed(SreTools.FIND_TOP_LOG_ERRORS, context),
                        scoped(
                            SreTools.GET_KUBERNETES_SERVICE_EVENTS,
                            context,
                        ),
                        windowed(SreTools.FIND_SERVICE_TRACES, context),
                    )
                },
        )

    fun forType(type: InvestigationType): Playbook = BY_TYPE.getValue(type)

    fun toolNames(probe: PlaybookContext): Set<String> =
        BY_TYPE.values
            .flatMap { it.steps(probe) }
            .mapTo(mutableSetOf()) { it.tool }

    private fun scoped(
        tool: String,
        context: PlaybookContext,
    ): ToolStep =
        ToolSteps.scoped(
            tool,
            context.target.service,
            context.target.namespace,
        )

    private fun windowed(
        tool: String,
        context: PlaybookContext,
    ): ToolStep =
        ToolSteps.windowed(
            tool,
            context.target.service,
            context.target.namespace,
            context.lookback,
        )

    private fun textSearch(context: PlaybookContext): ToolStep? =
        context.literal?.let { literal ->
            ToolSteps.textSearch(
                context.target.service,
                context.target.namespace,
                context.lookback,
                literal,
            )
        }

    private fun userTraces(context: PlaybookContext): ToolStep? =
        context.literal?.let { literal ->
            ToolSteps.userTraces(
                context.target.service,
                context.target.namespace,
                context.lookback,
                literal,
            )
        }
}
