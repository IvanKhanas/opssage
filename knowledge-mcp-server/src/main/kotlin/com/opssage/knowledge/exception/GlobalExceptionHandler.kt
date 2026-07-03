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
package com.opssage.knowledge.exception

import com.opssage.knowledge.dto.ApiErrorResponse
import org.slf4j.LoggerFactory

import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ServerWebInputException

private typealias ErrorResponse = ResponseEntity<ApiErrorResponse>

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleNotFound(exception: ResourceNotFoundException): ErrorResponse =
        response(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.message)

    @ExceptionHandler(WebExchangeBindException::class)
    fun handleValidation(exception: WebExchangeBindException): ErrorResponse {
        val message =
            exception.fieldErrors
                .joinToString("; ") { error ->
                    "${error.field}: ${error.defaultMessage}"
                }

        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message)
    }

    @ExceptionHandler(ServerWebInputException::class)
    fun handleInvalidInput(exception: ServerWebInputException): ErrorResponse =
        response(
            HttpStatus.BAD_REQUEST,
            "INVALID_REQUEST",
            exception.reason ?: "Invalid request",
        )

    @ExceptionHandler(DuplicateKeyException::class)
    fun handleDuplicateKey(): ErrorResponse =
        response(
            HttpStatus.CONFLICT,
            "RESOURCE_ALREADY_EXISTS",
            "A resource with the same unique value already exists",
        )

    @ExceptionHandler(InvalidFactStateException::class)
    fun handleStateConflict(error: InvalidFactStateException): ErrorResponse =
        response(
            HttpStatus.CONFLICT,
            "INVALID_STATE_TRANSITION",
            error.message,
        )

    @ExceptionHandler(InvalidSkillProposalStateException::class)
    fun handleSkillStateConflict(
        error: InvalidSkillProposalStateException,
    ): ErrorResponse =
        response(
            HttpStatus.CONFLICT,
            "INVALID_STATE_TRANSITION",
            error.message,
        )

    @ExceptionHandler(InvalidRequestException::class)
    fun handleInvalidRequest(error: InvalidRequestException): ErrorResponse =
        response(
            HttpStatus.BAD_REQUEST,
            "INVALID_REQUEST",
            error.message,
        )

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(exception: Exception): ErrorResponse {
        logger.error("Unexpected request processing error", exception)

        return response(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_ERROR",
            "An unexpected error occurred",
        )
    }

    private fun response(
        status: HttpStatus,
        code: String,
        message: String?,
    ): ErrorResponse =
        ResponseEntity
            .status(status)
            .body(ApiErrorResponse(code, message ?: status.reasonPhrase))
}
