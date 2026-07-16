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

import com.opssage.admin.model.InvestigationMessageRecord

import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query

interface InvestigationMessageMongoRepository :
    MongoRepository<InvestigationMessageRecord, String> {

    @Query(
        """
        {
          "command.metadata.messageId": ?0
        }
        """,
    )
    fun findByMessageId(messageId: String): InvestigationMessageRecord?

    @Query(
        """
        {
          "command.metadata.requestId": ?0,
          "command.owner.userId": ?1
        }
        """,
    )
    fun findByRequestIdAndUserId(
        requestId: String,
        userId: String,
        pageable: Pageable,
    ): List<InvestigationMessageRecord>

    @Query(
        """
        {
          "command.metadata.messageId": ?0,
          "command.owner.userId": ?1
        }
        """,
    )
    fun findByMessageIdAndUserId(
        messageId: String,
        userId: String,
    ): InvestigationMessageRecord?
}
