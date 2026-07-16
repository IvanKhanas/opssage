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
package com.opssage.admin.controller

import com.opssage.admin.dto.CreateInvestigationMessageRequest
import com.opssage.admin.dto.CreateInvestigationRequest
import com.opssage.admin.dto.InvestigationAcceptedResponse
import com.opssage.admin.dto.InvestigationMessageAcceptedResponse
import com.opssage.admin.dto.InvestigationMessageResponse
import com.opssage.admin.dto.InvestigationResponse
import com.opssage.admin.dto.toResponse
import com.opssage.admin.security.CurrentUser
import com.opssage.admin.service.ConversationMessageIntakeService
import com.opssage.admin.service.InvestigationIntakeService
import com.opssage.admin.service.InvestigationMessageStore
import com.opssage.admin.service.InvestigationRequestStore
import jakarta.validation.Valid

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/v1/investigations")
class InvestigationController(
    private val services: InvestigationEndpointServices,
    private val currentUser: CurrentUser,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun submit(
        @Valid @RequestBody request: CreateInvestigationRequest,
    ): InvestigationAcceptedResponse {
        val requestId =
            services.intake.submit(request, currentUser.requirePrincipal())
        return InvestigationAcceptedResponse(requestId, "ACCEPTED")
    }

    @GetMapping("/{requestId}")
    fun findByRequestId(
        @PathVariable requestId: String,
    ): InvestigationResponse =
        services.requests
            .findOwned(requestId, currentUser.requirePrincipal())
            ?.toResponse()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

    @GetMapping
    fun findAll(
        @RequestParam(defaultValue = "20") limit: Int,
    ): List<InvestigationResponse> =
        services.requests
            .listOwned(currentUser.requirePrincipal(), limit)
            .map { it.toResponse() }

    @PostMapping("/{requestId}/messages")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun submitMessage(
        @PathVariable requestId: String,
        @Valid @RequestBody request: CreateInvestigationMessageRequest,
    ): InvestigationMessageAcceptedResponse {
        val messageId =
            services.messages.intake.submit(
                requestId,
                request,
                currentUser.requirePrincipal(),
            )
        return InvestigationMessageAcceptedResponse(messageId, "ACCEPTED")
    }

    @GetMapping("/{requestId}/messages")
    fun findMessages(
        @PathVariable requestId: String,
        @RequestParam(defaultValue = "20") limit: Int,
    ): List<InvestigationMessageResponse> =
        services.messages.store
            .listOwned(requestId, currentUser.requirePrincipal(), limit)
            .map { it.toResponse() }

    @GetMapping("/{requestId}/messages/{messageId}")
    fun findMessage(
        @PathVariable requestId: String,
        @PathVariable messageId: String,
    ): InvestigationMessageResponse =
        services.messages.store
            .findOwned(messageId, currentUser.requirePrincipal())
            ?.takeIf { it.command.metadata.requestId == requestId }
            ?.toResponse()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
}

@Component
class InvestigationEndpointServices(
    val intake: InvestigationIntakeService,
    val requests: InvestigationRequestStore,
    val messages: InvestigationMessageEndpointServices,
)

@Component
class InvestigationMessageEndpointServices(
    val intake: ConversationMessageIntakeService,
    val store: InvestigationMessageStore,
)
