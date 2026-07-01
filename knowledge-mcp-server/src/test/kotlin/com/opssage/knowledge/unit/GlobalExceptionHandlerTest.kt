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
package com.opssage.knowledge.unit

import com.opssage.knowledge.exception.GlobalExceptionHandler
import com.opssage.knowledge.exception.InvalidFactStateException
import com.opssage.knowledge.exception.InvalidRequestException
import com.opssage.knowledge.exception.ResourceNotFoundException
import com.opssage.knowledge.model.FactStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import org.springframework.http.HttpStatus
import org.springframework.web.server.ServerWebInputException

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `not found exception produces a safe 404 response`() {
        val response =
            handler.handleNotFound(
                ResourceNotFoundException("Fact", "fact-1"),
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(response.body?.code).isEqualTo("RESOURCE_NOT_FOUND")
        assertThat(
            response.body?.message,
        ).isEqualTo("Fact with id 'fact-1' was not found")
    }

    @Test
    fun `duplicate key produces a safe 409 response`() {
        val response = handler.handleDuplicateKey()

        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(response.body?.code).isEqualTo("RESOURCE_ALREADY_EXISTS")
    }

    @Test
    fun `state conflict exception produces 409 with transition message`() {
        val response =
            handler.handleStateConflict(
                InvalidFactStateException(
                    FactStatus.REJECTED,
                    FactStatus.APPROVED,
                ),
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(response.body?.code).isEqualTo("INVALID_STATE_TRANSITION")
        assertThat(
            response.body?.message,
        ).contains("REJECTED").contains("APPROVED")
    }

    @Test
    fun `invalid request exception produces 400`() {
        val response =
            handler.handleInvalidRequest(
                InvalidRequestException("approvedBy must not be blank"),
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.code).isEqualTo("INVALID_REQUEST")
        assertThat(
            response.body?.message,
        ).isEqualTo("approvedBy must not be blank")
    }

    @Test
    fun `invalid input exception produces 400 with reason`() {
        val response =
            handler.handleInvalidInput(
                ServerWebInputException("Invalid JSON"),
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.code).isEqualTo("INVALID_REQUEST")
        assertThat(response.body?.message).isEqualTo("Invalid JSON")
    }

    @Test
    fun `unexpected exception produces 500 without leaking internal details`() {
        val response =
            handler.handleUnexpected(
                RuntimeException("database connection refused"),
            )

        assertThat(
            response.statusCode,
        ).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        assertThat(response.body?.code).isEqualTo("INTERNAL_ERROR")
        assertThat(
            response.body?.message,
        ).doesNotContain("database connection refused")
    }
}
