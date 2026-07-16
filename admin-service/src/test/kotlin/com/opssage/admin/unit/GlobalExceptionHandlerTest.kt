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

import com.opssage.admin.exception.GlobalExceptionHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `maps response status exception to api error`() {
        val response =
            handler.handleStatus(
                ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "conversation_not_ready",
                ),
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(response.body?.error).isEqualTo("conversation_not_ready")
    }

    @Test
    fun `maps unexpected exception to internal error`() {
        val response = handler.handleUnexpected(RuntimeException("boom"))

        assertThat(response.statusCode)
            .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        assertThat(response.body?.error).isEqualTo("internal_error")
    }
}
