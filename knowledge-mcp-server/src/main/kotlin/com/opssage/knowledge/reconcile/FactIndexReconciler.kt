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
package com.opssage.knowledge.reconcile

import com.opssage.knowledge.config.ReconciliationProperties
import com.opssage.knowledge.repository.FactRepository
import com.opssage.knowledge.repository.FactVectorIndex
import io.github.oshai.kotlinlogging.KotlinLogging

import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class FactIndexReconciler(
    private val repo: FactRepository,
    private val index: FactVectorIndex,
    private val properties: ReconciliationProperties,
) {

    @Order(1)
    @EventListener(ApplicationReadyEvent::class)
    fun reconcile() {
        val count =
            repo
                .findAll()
                .filter { fact -> fact.embedding == null }
                .flatMap({ fact ->
                    index
                        .embedding(fact)
                        .flatMap { embedding ->
                            repo.save(fact.copy(embedding = embedding))
                        }
                }, properties.embedConcurrency)
                .count()
                .block(properties.backfillTimeout)
        log.atInfo {
            message = "Backfilled fact embeddings"
            payload = mapOf("count" to count)
        }
    }
}
