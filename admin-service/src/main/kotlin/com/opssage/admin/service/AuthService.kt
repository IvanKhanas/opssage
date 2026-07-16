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

import com.opssage.admin.dto.LoginRequest
import com.opssage.admin.dto.RegisterRequest
import com.opssage.admin.model.AdminUser
import com.opssage.admin.model.UserCredentials
import com.opssage.admin.model.UserProfile
import com.opssage.admin.security.AuthStores
import com.opssage.admin.security.AuthTokens
import com.opssage.admin.security.TokenIssuer
import com.opssage.admin.security.UserPrincipal

import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class AuthService(
    private val stores: AuthStores,
    private val passwordEncoder: PasswordEncoder,
    private val tokenIssuer: TokenIssuer,
) {

    @Transactional
    fun register(request: RegisterRequest): AuthTokens {
        val email = normalize(request.email)
        val password = requireNotNull(request.password)
        if (stores.users.existsByCredentialsEmail(email)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "email_taken")
        }
        val user =
            stores.users.save(
                AdminUser(
                    credentials =
                        UserCredentials(
                            email = email,
                            passwordHash =
                                requireNotNull(
                                    passwordEncoder.encode(password),
                                ),
                        ),
                    profile = UserProfile(),
                ),
            )
        return tokenIssuer.issue(principal(user))
    }

    fun login(request: LoginRequest): AuthTokens {
        val user =
            stores.users.findByCredentialsEmail(normalize(request.email))
                ?: throw unauthorized()
        if (
            !passwordEncoder.matches(
                request.password,
                user.credentials.passwordHash,
            )
        ) {
            throw unauthorized()
        }
        return tokenIssuer.issue(principal(user))
    }

    @Transactional
    fun refresh(refreshToken: String?): AuthTokens {
        val userId =
            refreshToken
                ?.let { stores.refreshTokens.consume(it) }
                ?: throw unauthorized()
        val user = stores.users.findById(userId).orElseThrow { unauthorized() }
        return tokenIssuer.issue(principal(user))
    }

    @Transactional
    fun logout(principal: UserPrincipal?) {
        principal?.let { stores.refreshTokens.revokeUserTokens(it.userId) }
    }

    private fun principal(user: AdminUser): UserPrincipal =
        UserPrincipal(
            userId = requireNotNull(user.id),
            email = user.credentials.email,
            roles = user.profile.roles,
        )

    private fun normalize(email: String): String = email.trim().lowercase()

    private fun unauthorized(): ResponseStatusException =
        ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid_credentials")
}
