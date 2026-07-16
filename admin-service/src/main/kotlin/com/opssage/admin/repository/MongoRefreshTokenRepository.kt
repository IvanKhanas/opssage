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
package com.opssage.admin.repository

import com.opssage.admin.model.RefreshToken

import org.springframework.stereotype.Repository

@Repository
class MongoRefreshTokenRepository(
    private val data: RefreshTokenMongoRepository,
) : RefreshTokenRepository {

    override fun save(token: RefreshToken): RefreshToken = data.save(token)

    override fun findBySecretTokenHash(tokenHash: String): RefreshToken? =
        data.findBySecretTokenHash(tokenHash)

    override fun deleteBySecretTokenHash(tokenHash: String): Long =
        data.deleteBySecretTokenHash(tokenHash)

    override fun deleteByOwnerUserId(userId: String) =
        data.deleteByOwnerUserId(userId)
}
