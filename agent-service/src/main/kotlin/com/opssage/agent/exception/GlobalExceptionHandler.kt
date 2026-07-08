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
package com.opssage.agent.exception

import com.opssage.agent.dto.ApiError
import io.github.oshai.kotlinlogging.KotlinLogging

import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

private val log = KotlinLogging.logger {}

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(InvestigationFailedException::class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    fun handleInvestigationFailed(ex: InvestigationFailedException): ApiError =
        ApiError(
            error = "investigation_failed",
            message = ex.message ?: "Investigation failed",
        )

    @ExceptionHandler(ConversationNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleConversationNotFound(
        ex: ConversationNotFoundException,
    ): ApiError =
        ApiError(
            error = "conversation_not_found",
            message = ex.message ?: "Conversation not found",
        )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidation(ex: MethodArgumentNotValidException): ApiError =
        ApiError(
            error = "validation_error",
            message = validationMessage(ex),
        )

    @ExceptionHandler(HttpMessageNotReadableException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleUnreadableBody(): ApiError =
        ApiError(
            error = "invalid_request",
            message = "Request body is missing or malformed",
        )

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleUnexpected(ex: Exception): ApiError {
        log.atError {
            message = "Unexpected request processing error"
            cause = ex
        }
        return ApiError(
            error = "internal_error",
            message = "An unexpected error occurred",
        )
    }

    private fun validationMessage(
        ex: MethodArgumentNotValidException,
    ): String =
        ex.bindingResult.fieldErrors.joinToString("; ") { error ->
            "${error.field}: ${error.defaultMessage}"
        }
}
