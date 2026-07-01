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
package com.opssage.knowledge.util

import com.opssage.knowledge.exception.ResourceNotFoundException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

private val worker = Schedulers.boundedElastic()

private fun <T : Any> Mono<T>.onWorker(): Mono<T> = subscribeOn(worker)

@Suppress("ktlint:standard:function-expression-body")
fun <T : Any> Flux<T>.blockingList(): List<T> {
    return collectList().onWorker().block() ?: emptyList()
}

fun <T : Any> Mono<T>.blockingGet(): T? = onWorker().block()

fun <T : Any> Mono<T>.orNotFound(
    resource: String,
    id: String,
): Mono<T> =
    switchIfEmpty(
        Mono.error(ResourceNotFoundException(resource, id)),
    )
