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
package com.opssage.knowledge.service

import com.opssage.knowledge.exception.InvalidRequestException
import com.opssage.knowledge.exception.InvalidSkillProposalStateException
import com.opssage.knowledge.model.SkillProposal
import com.opssage.knowledge.model.SkillProposalDraft
import com.opssage.knowledge.model.SkillProposalStatus
import com.opssage.knowledge.repository.SkillProposalRepository
import com.opssage.knowledge.util.orNotFound
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import java.time.Instant

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private typealias Proposals = Flux<SkillProposal>

@Service
class SkillProposalService(
    private val repo: SkillProposalRepository,
) {

    fun findById(id: String): Mono<SkillProposal> =
        repo
            .findById(id)
            .orNotFound("SkillProposal", id)

    fun findByStatus(status: SkillProposalStatus): Proposals =
        repo.findByStatus(status)

    fun propose(draft: SkillProposalDraft): Mono<SkillProposal> =
        repo.save(
            SkillProposal(
                title = draft.title,
                problem = draft.problem,
                proposedToolName = draft.proposedToolName,
                expectedInputs = draft.expectedInputs,
                expectedOutputs = draft.expectedOutputs,
                motivation = draft.motivation,
                examples = draft.examples,
            ),
        )

    @Transactional
    fun approve(
        id: String,
        reviewedBy: String,
    ): Mono<SkillProposal> {
        if (reviewedBy.isBlank()) {
            return Mono.error(
                InvalidRequestException("reviewedBy must not be blank"),
            )
        }

        val existing =
            repo
                .findById(id)
                .orNotFound("SkillProposal", id)

        return existing.flatMap { proposal ->
            if (proposal.status != SkillProposalStatus.PROPOSED) {
                return@flatMap Mono.error(
                    InvalidSkillProposalStateException(
                        proposal.status,
                        SkillProposalStatus.APPROVED,
                    ),
                )
            }

            repo.save(
                proposal.copy(
                    status = SkillProposalStatus.APPROVED,
                    reviewedBy = reviewedBy,
                    reviewedAt = Instant.now(),
                ),
            )
        }
    }

    @Transactional
    fun reject(
        id: String,
        reviewedBy: String,
    ): Mono<SkillProposal> {
        if (reviewedBy.isBlank()) {
            return Mono.error(
                InvalidRequestException("reviewedBy must not be blank"),
            )
        }

        val existing =
            repo
                .findById(id)
                .orNotFound("SkillProposal", id)

        return existing.flatMap { proposal ->
            if (proposal.status != SkillProposalStatus.PROPOSED) {
                return@flatMap Mono.error(
                    InvalidSkillProposalStateException(
                        proposal.status,
                        SkillProposalStatus.REJECTED,
                    ),
                )
            }

            repo.save(
                proposal.copy(
                    status = SkillProposalStatus.REJECTED,
                    reviewedBy = reviewedBy,
                    reviewedAt = Instant.now(),
                ),
            )
        }
    }

    @Transactional
    fun delete(id: String): Mono<Void> {
        val existing =
            repo
                .findById(id)
                .orNotFound("SkillProposal", id)

        return existing.flatMap { repo.deleteById(id) }
    }
}
