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
package com.opssage.knowledge.controller

import com.opssage.knowledge.config.PaginationProperties
import com.opssage.knowledge.dto.CreateInvestigationSummaryRequest
import com.opssage.knowledge.model.InvestigationSummary
import com.opssage.knowledge.service.InvestigationSummaryService
import com.opssage.knowledge.util.paged
import jakarta.validation.Valid
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/investigation-summaries")
class InvestigationSummaryController(
    private val investigationSummaryService: InvestigationSummaryService,
    private val pagination: PaginationProperties,
) {

    @GetMapping
    fun findAll(
        @RequestParam(required = false) investigationId: String?,
        @RequestParam(required = false) serviceId: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(required = false) size: Int?,
    ): Flux<InvestigationSummary> =
        when {
            investigationId != null ->
                investigationSummaryService.findByInvestigation(investigationId)
            serviceId != null ->
                investigationSummaryService.findByService(serviceId)
            else -> investigationSummaryService.findAll()
        }.paged(page, pagination.resolveSize(size))

    @GetMapping("/{id}")
    fun findById(
        @PathVariable id: String,
    ): Mono<InvestigationSummary> = investigationSummaryService.findById(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: CreateInvestigationSummaryRequest,
    ): Mono<InvestigationSummary> =
        investigationSummaryService.save(request.toDraft())

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: String,
    ): Mono<Void> = investigationSummaryService.delete(id)
}
