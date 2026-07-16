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
import com.opssage.admin.model.RefreshToken
import com.opssage.admin.model.RefreshTokenOwner
import com.opssage.admin.model.RefreshTokenSecret
import com.opssage.admin.repository.RefreshTokenRepository

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64

import org.springframework.stereotype.Component

@Component
class RefreshTokenService(
    private val repository: RefreshTokenRepository,
    private val properties: AuthProperties,
    private val clock: Clock,
) {

    private val random = SecureRandom()

    fun issue(userId: String): String {
        val token = randomToken()
        repository.save(
            RefreshToken(
                owner = RefreshTokenOwner(userId = userId),
                secret =
                    RefreshTokenSecret(
                        tokenHash = hash(token),
                        expiresAt =
                            clock.instant().plus(properties.refresh.ttl),
                    ),
            ),
        )
        return token
    }

    fun consume(token: String): String? {
        val tokenHash = hash(token)
        val saved = repository.findBySecretTokenHash(tokenHash) ?: return null
        if (repository.deleteBySecretTokenHash(tokenHash) == 0L) {
            return null
        }
        if (saved.secret.expiresAt.isBefore(clock.instant())) {
            return null
        }
        return saved.owner.userId
    }

    fun revokeUserTokens(userId: String) {
        repository.deleteByOwnerUserId(userId)
    }

    private fun randomToken(): String {
        val bytes = ByteArray(REFRESH_TOKEN_BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hash(token: String): String {
        val digest =
            MessageDigest
                .getInstance(SHA_256)
                .digest(token.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private companion object {
        const val REFRESH_TOKEN_BYTES = 32
        const val SHA_256 = "SHA-256"
    }
}
