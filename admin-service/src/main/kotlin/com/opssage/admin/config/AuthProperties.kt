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
package com.opssage.admin.config

import java.time.Duration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("admin.auth")
data class AuthProperties(
    val jwt: JwtProperties,
    val refresh: RefreshProperties,
    val cookies: CookieProperties,
)

data class JwtProperties(
    val secret: String,
    val accessTtl: Duration,
)

data class RefreshProperties(
    val ttl: Duration,
)

data class CookieProperties(
    val accessName: String,
    val refreshName: String,
    val secure: Boolean,
)
