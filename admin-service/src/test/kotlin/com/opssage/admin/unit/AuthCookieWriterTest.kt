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

import com.opssage.admin.model.UserRole
import com.opssage.admin.security.AuthCookieWriter
import com.opssage.admin.security.AuthTokens
import com.opssage.admin.security.UserPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import org.springframework.mock.web.MockHttpServletResponse

class AuthCookieWriterTest {

    @Test
    fun `writes secure http only auth cookies`() {
        val response = MockHttpServletResponse()
        val writer = AuthCookieWriter(AuthTestFixtures.authProperties(true))

        writer.write(response, tokens())

        assertThat(response.getHeaders("Set-Cookie"))
            .anySatisfy {
                assertThat(it)
                    .contains("opssage_access=access")
                    .contains("HttpOnly")
                    .contains("Secure")
                    .contains("SameSite=Strict")
            }.anySatisfy {
                assertThat(it).contains("opssage_refresh=refresh")
            }
    }

    @Test
    fun `clears auth cookies`() {
        val response = MockHttpServletResponse()
        val writer = AuthCookieWriter(AuthTestFixtures.authProperties())

        writer.clear(response)

        assertThat(response.getHeaders("Set-Cookie"))
            .allSatisfy { assertThat(it).contains("Max-Age=0") }
    }

    private fun tokens(): AuthTokens =
        AuthTokens(
            accessToken = "access",
            refreshToken = "refresh",
            principal =
                UserPrincipal(
                    userId = "user-1",
                    email = "sre@example.com",
                    roles = setOf(UserRole.SRE),
                ),
        )
}
