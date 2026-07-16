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

import com.opssage.admin.controller.AuthController
import com.opssage.admin.controller.AuthEndpointServices
import com.opssage.admin.dto.LoginRequest
import com.opssage.admin.dto.RegisterRequest
import com.opssage.admin.model.UserRole
import com.opssage.admin.security.AuthCookieWriter
import com.opssage.admin.security.AuthTokens
import com.opssage.admin.security.CurrentUser
import com.opssage.admin.security.UserPrincipal
import com.opssage.admin.service.AuthService
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

import org.springframework.mock.web.MockHttpServletResponse

@ExtendWith(MockKExtension::class)
class AuthControllerTest {

    @MockK
    lateinit var authService: AuthService

    @MockK
    lateinit var cookies: AuthCookieWriter

    @MockK
    lateinit var currentUser: CurrentUser

    private lateinit var controller: AuthController

    @BeforeEach
    fun setUp() {
        controller =
            AuthController(
                AuthEndpointServices(authService, cookies, currentUser),
            )
    }

    @Test
    fun `register writes cookies and returns user`() {
        val response = MockHttpServletResponse()
        val request = RegisterRequest("sre@example.com", "password-1")
        every { authService.register(request) } returns tokens()
        justRun { cookies.write(response, tokens()) }

        val body = controller.register(request, response)

        assertThat(body.userId).isEqualTo("user-1")
        verify { cookies.write(response, tokens()) }
    }

    @Test
    fun `login writes cookies and returns user`() {
        val response = MockHttpServletResponse()
        val request = LoginRequest("sre@example.com", "password-1")
        every { authService.login(request) } returns tokens()
        justRun { cookies.write(response, tokens()) }

        val body = controller.login(request, response)

        assertThat(body.email).isEqualTo("sre@example.com")
        verify { cookies.write(response, tokens()) }
    }

    @Test
    fun `refresh rotates cookies`() {
        val response = MockHttpServletResponse()
        every { authService.refresh("refresh") } returns tokens()
        justRun { cookies.write(response, tokens()) }

        val body = controller.refresh("refresh", response)

        assertThat(body.roles).containsExactly(UserRole.SRE)
        verify { cookies.write(response, tokens()) }
    }

    @Test
    fun `logout revokes tokens and clears cookies`() {
        val response = MockHttpServletResponse()
        justRun { authService.logout(principal()) }
        justRun { cookies.clear(response) }

        controller.logout(principal(), response)

        verify { authService.logout(principal()) }
        verify { cookies.clear(response) }
    }

    @Test
    fun `me returns current user`() {
        every { currentUser.requirePrincipal() } returns principal()

        val body = controller.me()

        assertThat(body.userId).isEqualTo("user-1")
    }

    private fun tokens(): AuthTokens =
        AuthTokens(
            accessToken = "access",
            refreshToken = "refresh",
            principal = principal(),
        )

    private fun principal(): UserPrincipal =
        UserPrincipal(
            userId = "user-1",
            email = "sre@example.com",
            roles = setOf(UserRole.SRE),
        )
}
