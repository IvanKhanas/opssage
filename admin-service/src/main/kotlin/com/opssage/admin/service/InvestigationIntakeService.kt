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
package com.opssage.admin.service

import com.opssage.admin.dto.CreateInvestigationRequest
import com.opssage.admin.messaging.InvestigationCommandOutbox
import com.opssage.admin.security.UserPrincipal

import java.util.UUID

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InvestigationIntakeService(
    private val store: InvestigationRequestStore,
    private val commandOutbox: InvestigationCommandOutbox,
) {

    @Transactional
    fun submit(
        request: CreateInvestigationRequest,
        principal: UserPrincipal,
    ): String {
        val requestId = UUID.randomUUID().toString()
        store.create(requestId, request, principal)
        val command = request.toCommand(requestId, principal)
        commandOutbox.enqueue(command)
        return requestId
    }
}
