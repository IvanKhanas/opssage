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
package com.opssage.agent.investigation

import com.opssage.agent.config.ConfidenceProperties
import com.opssage.agent.model.Confidence
import com.opssage.agent.model.InvestigationType
import com.opssage.agent.model.Observation
import com.opssage.agent.playbook.SreTools
import com.opssage.agent.playbook.ToolFields
import com.opssage.agent.tools.ToolOutputReader

import org.springframework.stereotype.Component

@Component
class ConfidenceCalculator(
    private val properties: ConfidenceProperties,
    private val reader: ToolOutputReader,
) {

    fun reconcile(inputs: ConfidenceInputs): Confidence {
        if (!inputs.grounded) {
            return Confidence.LOW
        }
        val cap =
            minOf(
                evidenceCap(inputs.evidenceCount),
                confirmationCap(inputs),
            )
        val floor = observedFloor(inputs.observations)
        return minOf(maxOf(inputs.reported, floor), cap)
    }

    private fun evidenceCap(evidenceCount: Int): Confidence =
        when {
            evidenceCount >= properties.highEvidenceThreshold ->
                Confidence.HIGH

            evidenceCount >= properties.mediumEvidenceThreshold ->
                Confidence.MEDIUM

            else -> Confidence.LOW
        }

    private fun confirmationCap(inputs: ConfidenceInputs): Confidence {
        val observations = inputs.observations
        if (missesUserEvidence(inputs.type, observations)) {
            return Confidence.LOW
        }
        return if (observations.any(::confirmsCausalChain)) {
            Confidence.HIGH
        } else {
            Confidence.MEDIUM
        }
    }

    private fun missesUserEvidence(
        type: InvestigationType,
        observations: List<Observation>,
    ): Boolean {
        if (type != InvestigationType.USER_PROBLEM_INVESTIGATION) {
            return false
        }
        return observations.none(::linksToUser)
    }

    private fun linksToUser(observation: Observation): Boolean =
        when (observation.tool) {
            SreTools.FIND_USER_RELATED_TRACES ->
                hasEntries(observation, ToolFields.TRACES)

            SreTools.FIND_LOG_ERRORS_BY_TEXT ->
                hasEntries(observation, ToolFields.TOP_ERRORS)

            else -> false
        }

    private fun confirmsCausalChain(observation: Observation): Boolean =
        observation.tool in TRACE_TOOLS &&
            hasEntries(observation, ToolFields.TRACES)

    private fun hasEntries(
        observation: Observation,
        field: String,
    ): Boolean =
        observation.succeeded &&
            reader.hasEntries(observation.output, field)

    private fun observedFloor(observations: List<Observation>): Confidence =
        observations
            .filter(Observation::succeeded)
            .mapNotNull(::toolConfidence)
            .maxOrNull()
            ?.coerceAtMost(Confidence.MEDIUM)
            ?: Confidence.LOW

    private fun toolConfidence(observation: Observation): Confidence? {
        val root = reader.read(observation.output) ?: return null
        val reported = reader.text(root, CONFIDENCE_FIELD) ?: return null
        return runCatching { Confidence.valueOf(reported) }.getOrNull()
    }

    private companion object {
        const val CONFIDENCE_FIELD = "confidence"

        val TRACE_TOOLS =
            setOf(
                SreTools.FIND_SERVICE_TRACES,
                SreTools.FIND_USER_RELATED_TRACES,
            )
    }
}
