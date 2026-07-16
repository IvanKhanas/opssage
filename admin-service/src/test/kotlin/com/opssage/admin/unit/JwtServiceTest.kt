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
import com.opssage.admin.security.JwtService
import com.opssage.admin.security.UserPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.time.Clock

class JwtServiceTest {

    @Test
    fun `issues and verifies access token`() {
        val service =
            JwtService(
                AuthTestFixtures.authProperties(),
                Clock.systemUTC(),
            )

        val token =
            service.issue(
                UserPrincipal(
                    userId = "user-1",
                    email = "sre@example.com",
                    roles = setOf(UserRole.SRE),
                ),
            )

        val verified = service.verify(token)

        assertThat(verified.userId).isEqualTo("user-1")
        assertThat(verified.email).isEqualTo("sre@example.com")
        assertThat(verified.roles).containsExactly(UserRole.SRE)
    }
}
