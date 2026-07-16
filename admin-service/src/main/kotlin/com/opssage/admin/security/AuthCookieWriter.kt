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
package com.opssage.admin.security

import com.opssage.admin.config.AuthProperties
import jakarta.servlet.http.HttpServletResponse

import java.time.Duration

import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component

@Component
class AuthCookieWriter(
    private val properties: AuthProperties,
) {

    fun write(
        response: HttpServletResponse,
        tokens: AuthTokens,
    ) {
        response.addHeader(
            HttpHeaders.SET_COOKIE,
            accessCookie(tokens.accessToken, properties.jwt.accessTtl),
        )
        response.addHeader(
            HttpHeaders.SET_COOKIE,
            refreshCookie(tokens.refreshToken, properties.refresh.ttl),
        )
    }

    fun clear(response: HttpServletResponse) {
        response.addHeader(
            HttpHeaders.SET_COOKIE,
            accessCookie("", Duration.ZERO),
        )
        response.addHeader(
            HttpHeaders.SET_COOKIE,
            refreshCookie("", Duration.ZERO),
        )
    }

    private fun accessCookie(
        value: String,
        maxAge: Duration,
    ): String = cookie(properties.cookies.accessName, value, maxAge)

    private fun refreshCookie(
        value: String,
        maxAge: Duration,
    ): String = cookie(properties.cookies.refreshName, value, maxAge)

    private fun cookie(
        name: String,
        value: String,
        maxAge: Duration,
    ): String =
        ResponseCookie
            .from(name, value)
            .httpOnly(true)
            .secure(properties.cookies.secure)
            .sameSite(SAME_SITE)
            .path("/")
            .maxAge(maxAge)
            .build()
            .toString()

    private companion object {
        const val SAME_SITE = "Strict"
    }
}
