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

import com.opssage.admin.model.RefreshToken
import com.opssage.admin.model.RefreshTokenOwner
import com.opssage.admin.model.RefreshTokenSecret
import com.opssage.admin.repository.RefreshTokenRepository
import com.opssage.admin.security.RefreshTokenService
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@ExtendWith(MockKExtension::class)
class RefreshTokenServiceTest {

    @MockK
    lateinit var repository: RefreshTokenRepository

    private val saved = slot<RefreshToken>()
    private val clock =
        Clock.fixed(Instant.parse("2026-07-13T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `issues hashed refresh token`() {
        every { repository.save(capture(saved)) } answers { firstArg() }
        val service = service()

        val token = service.issue("user-1")

        assertThat(token).isNotBlank()
        assertThat(saved.captured.owner.userId).isEqualTo("user-1")
        assertThat(saved.captured.secret.tokenHash).doesNotContain(token)
    }

    @Test
    fun `consume deletes valid token and returns user id`() {
        every { repository.findBySecretTokenHash(any()) } returns savedToken()
        every { repository.deleteBySecretTokenHash(any()) } returns 1L

        val userId = service().consume("refresh")

        assertThat(userId).isEqualTo("user-1")
        verify { repository.deleteBySecretTokenHash(any()) }
    }

    @Test
    fun `consume deletes expired token and returns null`() {
        every { repository.findBySecretTokenHash(any()) } returns
            savedToken(expiresAt = Instant.parse("2026-07-13T09:00:00Z"))
        every { repository.deleteBySecretTokenHash(any()) } returns 1L

        val userId = service().consume("refresh")

        assertThat(userId).isNull()
        verify { repository.deleteBySecretTokenHash(any()) }
    }

    @Test
    fun `consume returns null when token deleted by concurrent caller`() {
        every { repository.findBySecretTokenHash(any()) } returns savedToken()
        every { repository.deleteBySecretTokenHash(any()) } returns 0L

        val userId = service().consume("refresh")

        assertThat(userId).isNull()
    }

    @Test
    fun `revokes all user refresh tokens`() {
        justRun { repository.deleteByOwnerUserId("user-1") }

        service().revokeUserTokens("user-1")

        verify { repository.deleteByOwnerUserId("user-1") }
    }

    private fun service(): RefreshTokenService =
        RefreshTokenService(
            repository,
            AuthTestFixtures.authProperties(),
            clock,
        )

    private fun savedToken(
        expiresAt: Instant = Instant.parse("2026-07-13T11:00:00Z"),
    ): RefreshToken =
        RefreshToken(
            id = "token-1",
            owner = RefreshTokenOwner(userId = "user-1"),
            secret =
                RefreshTokenSecret(
                    tokenHash = "hash",
                    expiresAt = expiresAt,
                ),
        )
}
