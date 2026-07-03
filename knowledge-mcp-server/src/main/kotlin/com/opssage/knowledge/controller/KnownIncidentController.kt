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
import com.opssage.knowledge.dto.CreateKnownIncidentRequest
import com.opssage.knowledge.dto.UpdateKnownIncidentRequest
import com.opssage.knowledge.model.KnownIncident
import com.opssage.knowledge.service.KnownIncidentService
import com.opssage.knowledge.util.paged
import jakarta.validation.Valid
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/known-incidents")
class KnownIncidentController(
    private val knownIncidentService: KnownIncidentService,
    private val pagination: PaginationProperties,
) {

    @GetMapping
    fun findAll(
        @RequestParam(required = false) serviceId: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(required = false) size: Int?,
    ): Flux<KnownIncident> =
        (
            serviceId
                ?.let { knownIncidentService.findByService(it) }
                ?: knownIncidentService.findAll()
        ).paged(page, pagination.resolveSize(size))

    @GetMapping("/{id}")
    fun findById(
        @PathVariable id: String,
    ): Mono<KnownIncident> = knownIncidentService.findById(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: CreateKnownIncidentRequest,
    ): Mono<KnownIncident> = knownIncidentService.create(request.toIncident())

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateKnownIncidentRequest,
    ): Mono<KnownIncident> =
        knownIncidentService.update(id, request.toIncident())

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: String,
    ): Mono<Void> = knownIncidentService.delete(id)
}
