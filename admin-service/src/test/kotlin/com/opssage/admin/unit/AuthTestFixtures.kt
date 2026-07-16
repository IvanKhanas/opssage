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

import com.opssage.admin.config.AuthProperties
import com.opssage.admin.config.CookieProperties
import com.opssage.admin.config.JwtProperties
import com.opssage.admin.config.RefreshProperties

import java.time.Duration

object AuthTestFixtures {

    fun authProperties(secure: Boolean = false): AuthProperties =
        AuthProperties(
            jwt =
                JwtProperties(
                    secret = "test-test-test-test-test-test-test-test",
                    accessTtl = Duration.ofMinutes(15),
                ),
            refresh = RefreshProperties(Duration.ofDays(30)),
            cookies =
                CookieProperties(
                    accessName = "opssage_access",
                    refreshName = "opssage_refresh",
                    secure = secure,
                ),
        )
}
