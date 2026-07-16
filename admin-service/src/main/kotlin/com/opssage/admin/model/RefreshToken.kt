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
package com.opssage.admin.model

import java.time.Instant

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document("refresh_tokens")
data class RefreshToken(
    @Id
    val id: String? = null,
    val owner: RefreshTokenOwner,
    val secret: RefreshTokenSecret,
)

data class RefreshTokenOwner(
    @Indexed
    val userId: String,
    val createdAt: Instant = Instant.now(),
)

data class RefreshTokenSecret(
    @Indexed(unique = true)
    val tokenHash: String,
    @Indexed(expireAfterSeconds = 0)
    val expiresAt: Instant,
)
