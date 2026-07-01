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

import com.opssage.knowledge.dto.CreateServiceProfileRequest
import com.opssage.knowledge.dto.UpdateServiceProfileRequest
import com.opssage.knowledge.model.ServiceProfile
import com.opssage.knowledge.service.ServiceProfileService
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
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

private typealias ProfileResult = Mono<ServiceProfile>

@RestController
@RequestMapping("/api/v1/service-profiles")
class ServiceProfileController(
    private val service: ServiceProfileService,
) {

    @GetMapping
    fun findAll(): Flux<ServiceProfile> = service.findAll()

    @GetMapping("/{id}")
    fun findById(
        @PathVariable id: String,
    ): Mono<ServiceProfile> = service.findById(id)

    @GetMapping("/by-service/{serviceId}")
    fun findByServiceId(
        @PathVariable serviceId: String,
    ): ProfileResult = service.findByServiceIdOrThrow(serviceId)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: CreateServiceProfileRequest,
    ): ProfileResult = service.create(request.toServiceProfile())

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateServiceProfileRequest,
    ): Mono<ServiceProfile> =
        service.update(
            id,
            request.toServiceProfile(),
        )

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: String,
    ): Mono<Void> = service.delete(id)
}
