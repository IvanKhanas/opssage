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

import com.opssage.admin.dto.AuthUserResponse
import com.opssage.admin.dto.CsrfTokenResponse
import com.opssage.admin.dto.LoginRequest
import com.opssage.admin.dto.RegisterRequest
import com.opssage.admin.security.AuthCookieWriter
import com.opssage.admin.security.AuthTokens
import com.opssage.admin.security.CurrentUser
import com.opssage.admin.security.UserPrincipal
import com.opssage.admin.service.AuthService
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid

import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val auth: AuthEndpointServices,
) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(
        @Valid @RequestBody request: RegisterRequest,
        response: HttpServletResponse,
    ): AuthUserResponse = write(auth.service.register(request), response)

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        response: HttpServletResponse,
    ): AuthUserResponse = write(auth.service.login(request), response)

    @PostMapping("/refresh")
    fun refresh(
        @CookieValue("\${admin.auth.cookies.refresh-name}", required = false)
        refreshToken: String?,
        response: HttpServletResponse,
    ): AuthUserResponse = write(auth.service.refresh(refreshToken), response)

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(
        @AuthenticationPrincipal principal: UserPrincipal?,
        response: HttpServletResponse,
    ) {
        auth.service.logout(principal)
        auth.cookies.clear(response)
    }

    @GetMapping("/me")
    fun me(): AuthUserResponse =
        AuthUserResponse.from(auth.currentUser.requirePrincipal())

    @GetMapping("/csrf")
    fun csrf(token: CsrfToken): CsrfTokenResponse =
        CsrfTokenResponse(
            headerName = token.headerName,
            parameterName = token.parameterName,
            token = token.token,
        )

    private fun write(
        tokens: AuthTokens,
        response: HttpServletResponse,
    ): AuthUserResponse {
        auth.cookies.write(response, tokens)
        return AuthUserResponse.from(tokens.principal)
    }
}

@Component
class AuthEndpointServices(
    val service: AuthService,
    val cookies: AuthCookieWriter,
    val currentUser: CurrentUser,
)
