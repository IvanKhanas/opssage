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
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val properties: AuthProperties,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = request.cookieValue(properties.cookies.accessName)
        if (token != null) {
            authenticate(token)
        }
        filterChain.doFilter(request, response)
    }

    private fun authenticate(token: String) {
        runCatching { jwtService.verify(token) }
            .onSuccess { principal ->
                val auth =
                    UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.authorities(),
                    )
                SecurityContextHolder.getContext().authentication = auth
            }.onFailure { error ->
                if (error !is JwtException) {
                    throw error
                }
                SecurityContextHolder.clearContext()
            }
    }

    private fun UserPrincipal.authorities(): List<SimpleGrantedAuthority> =
        roles.map { SimpleGrantedAuthority("ROLE_${it.name}") }

    private fun HttpServletRequest.cookieValue(name: String): String? =
        cookies?.firstOrNull { it.name == name }?.value
}
