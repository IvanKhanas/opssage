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
package com.opssage.admin.unit

import com.opssage.admin.dto.LoginRequest
import com.opssage.admin.dto.RegisterRequest
import com.opssage.admin.model.AdminUser
import com.opssage.admin.model.UserCredentials
import com.opssage.admin.model.UserProfile
import com.opssage.admin.repository.AdminUserRepository
import com.opssage.admin.security.AuthStores
import com.opssage.admin.security.JwtService
import com.opssage.admin.security.RefreshTokenService
import com.opssage.admin.security.TokenIssuer
import com.opssage.admin.security.UserPrincipal
import com.opssage.admin.service.AuthService
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

import java.util.Optional

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.web.server.ResponseStatusException

@ExtendWith(MockKExtension::class)
class AuthServiceTest {

    @MockK
    lateinit var users: AdminUserRepository

    @MockK
    lateinit var refreshTokens: RefreshTokenService

    @MockK
    lateinit var jwtService: JwtService

    private val passwordEncoder = BCryptPasswordEncoder()

    private lateinit var service: AuthService

    @BeforeEach
    fun setUp() {
        every { jwtService.issue(any()) } answers {
            "access-${firstArg<UserPrincipal>().userId}"
        }
        val tokenIssuer = TokenIssuer(jwtService, refreshTokens)
        service =
            AuthService(
                AuthStores(users, refreshTokens),
                passwordEncoder,
                tokenIssuer,
            )
    }

    @Test
    fun `register creates user and returns tokens`() {
        every {
            users.existsByCredentialsEmail("sre@example.com")
        } returns false
        every { refreshTokens.issue("user-1") } returns "refresh"
        every { users.save(any()) } answers {
            firstArg<AdminUser>().copy(id = "user-1")
        }

        val tokens =
            service.register(RegisterRequest("SRE@Example.com", "password-1"))

        assertThat(tokens.accessToken).isEqualTo("access-user-1")
        assertThat(tokens.refreshToken).isEqualTo("refresh")
        assertThat(tokens.principal.email).isEqualTo("sre@example.com")
    }

    @Test
    fun `register rejects duplicate email`() {
        every { users.existsByCredentialsEmail("sre@example.com") } returns true

        assertThatThrownBy {
            service.register(RegisterRequest("sre@example.com", "password-1"))
        }.isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `login rejects invalid password`() {
        every { users.findByCredentialsEmail("sre@example.com") } returns
            user(requireNotNull(passwordEncoder.encode("correct")))

        assertThatThrownBy {
            service.login(LoginRequest("sre@example.com", "wrong"))
        }.isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `refresh rotates refresh token`() {
        every { refreshTokens.consume("old-refresh") } returns "user-1"
        every { refreshTokens.issue("user-1") } returns "new-refresh"
        every { users.findById("user-1") } returns Optional.of(user())

        val tokens = service.refresh("old-refresh")

        assertThat(tokens.accessToken).isEqualTo("access-user-1")
        assertThat(tokens.refreshToken).isEqualTo("new-refresh")
    }

    @Test
    fun `logout revokes all user refresh tokens`() {
        justRun { refreshTokens.revokeUserTokens("user-1") }

        service.logout(userPrincipal())

        verify { refreshTokens.revokeUserTokens("user-1") }
    }

    private fun user(
        passwordHash: String =
            requireNotNull(passwordEncoder.encode("password-1")),
    ) = AdminUser(
        id = "user-1",
        credentials =
            UserCredentials(
                email = "sre@example.com",
                passwordHash = passwordHash,
            ),
        profile = UserProfile(),
    )

    private fun userPrincipal() =
        UserPrincipal(
            userId = "user-1",
            email = "sre@example.com",
            roles = user().profile.roles,
        )
}
