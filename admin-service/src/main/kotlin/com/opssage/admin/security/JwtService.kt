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
import com.opssage.admin.model.UserRole

import java.time.Clock

import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.stereotype.Component

@Component
class JwtService(
    private val properties: AuthProperties,
    private val clock: Clock,
) {

    init {
        val secretBytes = properties.jwt.secret.toByteArray(Charsets.UTF_8)
        require(secretBytes.size >= MIN_SECRET_BYTES) {
            "admin.auth.jwt.secret must be at least $MIN_SECRET_BYTES bytes; " +
                "set ADMIN_AUTH_JWT_SECRET"
        }
        require(!properties.jwt.secret.startsWith(PLACEHOLDER_PREFIX)) {
            "admin.auth.jwt.secret is the placeholder default; " +
                "set ADMIN_AUTH_JWT_SECRET"
        }
    }

    private val key: SecretKey =
        SecretKeySpec(
            properties.jwt.secret.toByteArray(Charsets.UTF_8),
            HMAC_ALGORITHM,
        )
    private val encoder: JwtEncoder =
        NimbusJwtEncoder.withSecretKey(key).build()
    private val decoder: JwtDecoder =
        NimbusJwtDecoder
            .withSecretKey(key)
            .macAlgorithm(MacAlgorithm.HS256)
            .build()

    fun issue(principal: UserPrincipal): String {
        val now = clock.instant()
        val claims =
            JwtClaimsSet
                .builder()
                .subject(principal.userId)
                .issuedAt(now)
                .expiresAt(now.plus(properties.jwt.accessTtl))
                .claim(CLAIM_EMAIL, principal.email)
                .claim(CLAIM_ROLES, principal.roles.map { it.name })
                .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return encoder
            .encode(JwtEncoderParameters.from(header, claims))
            .tokenValue
    }

    fun verify(token: String): UserPrincipal {
        val jwt = decoder.decode(token)
        val roles =
            jwt
                .getClaimAsStringList(CLAIM_ROLES)
                .orEmpty()
                .mapTo(mutableSetOf()) { UserRole.valueOf(it) }
        return UserPrincipal(
            userId = jwt.subject,
            email = jwt.getClaimAsString(CLAIM_EMAIL),
            roles = roles,
        )
    }

    private companion object {
        const val HMAC_ALGORITHM = "HmacSHA256"
        const val CLAIM_EMAIL = "email"
        const val CLAIM_ROLES = "roles"
        const val MIN_SECRET_BYTES = 32
        const val PLACEHOLDER_PREFIX = "change-me"
    }
}
