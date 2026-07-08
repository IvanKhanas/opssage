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
package com.opssage.agent.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("agent.masking")
data class MaskingProperties(
    val enabled: Boolean,
    val emailToken: String,
    val ipToken: String,
    val phoneToken: String,
    val secretToken: String,
    val uuidToken: String,
    val labelToken: String,
    val nameToken: String,
    val maskFullNames: Boolean,
    val sensitiveLabels: List<String>,
)
