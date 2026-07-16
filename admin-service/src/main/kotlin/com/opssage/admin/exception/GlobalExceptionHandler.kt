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
package com.opssage.admin.exception

import com.opssage.admin.dto.ApiError
import io.github.oshai.kotlinlogging.KotlinLogging

import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

private val log = KotlinLogging.logger {}

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(
        ex: MethodArgumentNotValidException,
    ): ResponseEntity<ApiError> =
        error(
            HttpStatus.BAD_REQUEST,
            "validation_failed",
            validationMessage(ex),
        )

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(): ResponseEntity<ApiError> =
        error(HttpStatus.BAD_REQUEST, "invalid_json", "Request body is invalid")

    @ExceptionHandler(ResponseStatusException::class)
    fun handleStatus(ex: ResponseStatusException): ResponseEntity<ApiError> =
        error(
            HttpStatus.valueOf(ex.statusCode.value()),
            statusCode(ex),
            ex.reason ?: "Request failed",
        )

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(): ResponseEntity<ApiError> =
        error(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication failed")

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(): ResponseEntity<ApiError> =
        error(HttpStatus.FORBIDDEN, "access_denied", "Access denied")

    @ExceptionHandler(DuplicateKeyException::class)
    fun handleDuplicateKey(): ResponseEntity<ApiError> =
        error(HttpStatus.CONFLICT, "duplicate", "Resource already exists")

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ApiError> {
        log.atError {
            message = "Unhandled admin-service exception"
            cause = ex
        }
        return error(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "internal_error",
            "Unexpected server error",
        )
    }

    private fun error(
        status: HttpStatus,
        code: String,
        message: String,
    ): ResponseEntity<ApiError> =
        ResponseEntity.status(status).body(ApiError(code, message))

    private fun validationMessage(
        ex: MethodArgumentNotValidException,
    ): String =
        ex.bindingResult
            .fieldErrors
            .joinToString("; ") { error ->
                "${error.field}: ${error.defaultMessage}"
            }.ifBlank { "Validation failed" }

    private fun statusCode(ex: ResponseStatusException): String =
        ex.reason ?: ex.statusCode.toString().lowercase()
}
