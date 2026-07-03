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

import com.opssage.knowledge.model.InvestigationSummary
import com.opssage.knowledge.model.InvestigationSummaryDraft
import com.opssage.knowledge.repository.InvestigationSummaryRepository
import com.opssage.knowledge.util.orNotFound
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private typealias Summaries = Flux<InvestigationSummary>

@Service
class InvestigationSummaryService(
    private val repo: InvestigationSummaryRepository,
) {

    fun findById(id: String): Mono<InvestigationSummary> =
        repo
            .findById(id)
            .orNotFound("InvestigationSummary", id)

    fun findByInvestigation(investigationId: String): Summaries =
        repo.findByInvestigationId(investigationId)

    fun findByService(serviceId: String): Summaries =
        repo.findByServiceId(serviceId)

    fun findAll(): Summaries = repo.findAll()

    fun save(draft: InvestigationSummaryDraft): Mono<InvestigationSummary> =
        repo.save(
            InvestigationSummary(
                investigationId = draft.investigationId,
                serviceId = draft.serviceId,
                summary = draft.summary,
                mostLikelyCause = draft.mostLikelyCause,
                confidence = draft.confidence,
                evidence = draft.evidence,
                recommendedActions = draft.recommendedActions,
            ),
        )

    @Transactional
    fun delete(id: String): Mono<Void> {
        val existing =
            repo
                .findById(id)
                .orNotFound("InvestigationSummary", id)

        return existing.flatMap { repo.deleteById(id) }
    }
}
