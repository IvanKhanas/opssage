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

import com.opssage.agent.model.InvestigationTarget
import com.opssage.agent.model.InvestigationType
import com.opssage.agent.playbook.PlaybookContext
import com.opssage.agent.playbook.Playbooks
import com.opssage.agent.playbook.SreTools
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

import java.time.Duration

class PlaybooksTest {

    @ParameterizedTest
    @EnumSource(InvestigationType::class)
    fun `every investigation type has a playbook with at least two steps`(
        type: InvestigationType,
    ) {
        val steps = Playbooks.forType(type).steps(context(literal = null))

        assertThat(steps).hasSizeGreaterThanOrEqualTo(2)
    }

    @ParameterizedTest
    @EnumSource(InvestigationType::class)
    fun `every step is scoped to the planned service and namespace`(
        type: InvestigationType,
    ) {
        val steps = Playbooks.forType(type).steps(context(literal = "a@b.com"))

        assertThat(steps).allSatisfy { step ->
            assertThat(step.arguments)
                .containsEntry(SreTools.SERVICE, "checkout")
                .containsEntry(SreTools.NAMESPACE, "prod")
        }
    }

    @Test
    fun `analytical request runs the fixed evidence sequence`() {
        val steps =
            Playbooks
                .forType(InvestigationType.ANALYTICAL_REQUEST)
                .steps(context(literal = "order-42"))

        assertThat(steps.map { it.tool }).containsExactly(
            SreTools.GET_SERVICE_HEALTH,
            SreTools.FIND_TOP_LOG_ERRORS,
            SreTools.FIND_LOG_ERRORS_BY_TEXT,
            SreTools.GET_SERVICE_CORRECTNESS,
            SreTools.FIND_SERVICE_TRACES,
        )
    }

    @Test
    fun `text search step carries the literal and is skipped without one`() {
        val playbook = Playbooks.forType(InvestigationType.ANALYTICAL_REQUEST)

        val withLiteral = playbook.steps(context(literal = "order-42"))
        val withoutLiteral = playbook.steps(context(literal = null))

        val textSearch =
            withLiteral.single {
                it.tool == SreTools.FIND_LOG_ERRORS_BY_TEXT
            }
        assertThat(textSearch.arguments)
            .containsEntry(SreTools.QUERY, "order-42")
        assertThat(withoutLiteral.map { it.tool })
            .doesNotContain(SreTools.FIND_LOG_ERRORS_BY_TEXT)
    }

    @Test
    fun `user problem investigation pivots on the reported identifier`() {
        val steps =
            Playbooks
                .forType(InvestigationType.USER_PROBLEM_INVESTIGATION)
                .steps(context(literal = "user@example.com"))

        assertThat(steps.map { it.tool }).containsExactly(
            SreTools.GET_SERVICE_HEALTH,
            SreTools.FIND_LOG_ERRORS_BY_TEXT,
            SreTools.FIND_USER_RELATED_TRACES,
            SreTools.FIND_TOP_LOG_ERRORS,
            SreTools.GET_SERVICE_CORRECTNESS,
        )
        val userTraces =
            steps.single { it.tool == SreTools.FIND_USER_RELATED_TRACES }
        assertThat(userTraces.arguments)
            .containsEntry(SreTools.USER_ID, "user@example.com")
    }

    @ParameterizedTest
    @EnumSource(
        value = InvestigationType::class,
        names = [
            "ALERT_INVESTIGATION",
            "USER_PROBLEM_INVESTIGATION",
            "ANALYTICAL_REQUEST",
        ],
    )
    fun `silent invariant breakage is collected, not only http symptoms`(
        type: InvestigationType,
    ) {
        val steps = Playbooks.forType(type).steps(context(literal = "id=123"))

        assertThat(steps.map { it.tool })
            .contains(SreTools.GET_SERVICE_CORRECTNESS)
    }

    @Test
    fun `windowed steps carry the anchor window as an ISO-8601 lookback`() {
        val steps =
            Playbooks
                .forType(InvestigationType.ALERT_INVESTIGATION)
                .steps(context(literal = null))

        assertThat(steps.first().arguments)
            .containsEntry(SreTools.LOOKBACK, "PT2H")
    }

    @Test
    fun `kubernetes step is not windowed because the tool reads live state`() {
        val steps =
            Playbooks
                .forType(InvestigationType.GENERAL_SERVICE_INVESTIGATION)
                .steps(context(literal = null))

        val kubernetes =
            steps.single {
                it.tool == SreTools.GET_KUBERNETES_SERVICE_EVENTS
            }
        assertThat(kubernetes.arguments).doesNotContainKey(SreTools.LOOKBACK)
    }

    @Test
    fun `probe context exposes every tool the playbooks can reach`() {
        val names = Playbooks.toolNames(context(literal = "probe"))

        assertThat(names).contains(
            SreTools.GET_SERVICE_HEALTH,
            SreTools.GET_SERVICE_CORRECTNESS,
            SreTools.GET_ALERT_CONTEXT,
            SreTools.GET_KUBERNETES_SERVICE_EVENTS,
            SreTools.FIND_TOP_LOG_ERRORS,
            SreTools.FIND_LOG_ERRORS_BY_TEXT,
            SreTools.FIND_SERVICE_TRACES,
            SreTools.FIND_USER_RELATED_TRACES,
        )
    }

    private fun context(literal: String?): PlaybookContext =
        PlaybookContext(
            target = InvestigationTarget("checkout", "prod"),
            lookback = Duration.ofHours(2),
            literal = literal,
        )
}
