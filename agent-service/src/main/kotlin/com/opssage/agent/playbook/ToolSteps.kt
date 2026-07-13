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

import java.time.Duration

object ToolSteps {

    fun scoped(
        tool: String,
        service: String,
        namespace: String,
    ): ToolStep =
        ToolStep(
            tool,
            mapOf(
                SreTools.SERVICE to service,
                SreTools.NAMESPACE to namespace,
            ),
        )

    fun windowed(
        tool: String,
        service: String,
        namespace: String,
        lookback: Duration,
    ): ToolStep =
        ToolStep(
            tool,
            scoped(tool, service, namespace).arguments +
                (SreTools.LOOKBACK to lookback.toString()),
        )

    fun textSearch(
        service: String,
        namespace: String,
        lookback: Duration,
        literal: String,
    ): ToolStep =
        withArgument(
            SreTools.FIND_LOG_ERRORS_BY_TEXT,
            service,
            namespace,
            lookback,
            SreTools.QUERY,
            literal,
        )

    fun userTraces(
        service: String,
        namespace: String,
        lookback: Duration,
        literal: String,
    ): ToolStep =
        withArgument(
            SreTools.FIND_USER_RELATED_TRACES,
            service,
            namespace,
            lookback,
            SreTools.USER_ID,
            literal,
        )

    private fun withArgument(
        tool: String,
        service: String,
        namespace: String,
        lookback: Duration,
        key: String,
        value: String,
    ): ToolStep =
        ToolStep(
            tool,
            windowed(tool, service, namespace, lookback).arguments +
                (key to value),
        )
}
