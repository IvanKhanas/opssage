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
package com.opssage.sre.unit

import com.opssage.sre.config.KubernetesProperties
import com.opssage.sre.config.KubernetesTokens
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path

class KubernetesTokensTest {

    @TempDir
    lateinit var directory: Path

    @Test
    fun `reads the projected service account token from disk`() {
        val path = write("projected-token\n")

        val tokens = KubernetesTokens(properties(path.toString()))

        assertThat(tokens.current()).isEqualTo("projected-token")
    }

    @Test
    fun `rereads the token after the kubelet rotates it`() {
        val path = write("first-token")
        val tokens = KubernetesTokens(properties(path.toString()))
        assertThat(tokens.current()).isEqualTo("first-token")

        Files.writeString(path, "rotated-token")

        assertThat(tokens.current()).isEqualTo("rotated-token")
    }

    @Test
    fun `falls back to the configured token when no file is mounted`() {
        val missing = directory.resolve("absent").toString()

        val tokens = KubernetesTokens(properties(missing))

        assertThat(tokens.current()).isEqualTo(STATIC_TOKEN)
    }

    @Test
    fun `falls back to the configured token when the file is empty`() {
        val path = write("   \n")

        val tokens = KubernetesTokens(properties(path.toString()))

        assertThat(tokens.current()).isEqualTo(STATIC_TOKEN)
    }

    @Test
    fun `falls back to the configured token when no path is set`() {
        val tokens = KubernetesTokens(properties(null))

        assertThat(tokens.current()).isEqualTo(STATIC_TOKEN)
    }

    @Test
    fun `never leaks the token through toString`() {
        val properties = properties("/var/run/token")

        assertThat(properties.toString()).doesNotContain(STATIC_TOKEN)
    }

    private fun write(content: String): Path =
        Files.writeString(directory.resolve("token"), content)

    private fun properties(tokenPath: String?): KubernetesProperties =
        KubernetesProperties(
            baseUrl = "https://kubernetes.default.svc",
            token = STATIC_TOKEN,
            tokenPath = tokenPath,
            appLabels = listOf("app"),
            caCertPath = null,
        )

    private companion object {
        const val STATIC_TOKEN = "static-token"
    }
}
