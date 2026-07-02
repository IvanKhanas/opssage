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
package com.opssage.knowledge.unit

import com.opssage.knowledge.exception.ResourceNotFoundException
import com.opssage.knowledge.model.ServiceProfile
import com.opssage.knowledge.repository.ServiceProfileRepository
import com.opssage.knowledge.service.ServiceProfileService
import com.opssage.knowledge.unit.fixture.ServiceProfileFixture
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@ExtendWith(MockKExtension::class)
class ServiceProfileServiceTest {

    @MockK
    lateinit var repository: ServiceProfileRepository

    private lateinit var service: ServiceProfileService

    @BeforeEach
    fun setUp() {
        service = ServiceProfileService(repository)
    }

    @Test
    fun `findById returns profile when found`() {
        val profile = ServiceProfileFixture.profile(id = "sp-1")
        every { repository.findById("sp-1") } returns Mono.just(profile)

        val result = service.findById("sp-1").block()!!

        assertThat(result.id).isEqualTo("sp-1")
    }

    @Test
    fun `findById throws when profile is missing`() {
        every { repository.findById("missing") } returns Mono.empty()

        assertThatThrownBy { service.findById("missing").block() }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun `findAll returns all profiles`() {
        val profiles =
            listOf(
                ServiceProfileFixture.profile(),
                ServiceProfileFixture.profile(),
            )
        every { repository.findAll() } returns Flux.fromIterable(profiles)

        val result = service.findAll().collectList().block()!!

        assertThat(result).hasSize(2)
        verify(exactly = 1) { repository.findAll() }
    }

    @Test
    fun `create saves and returns the profile`() {
        val profile = ServiceProfileFixture.profile(id = null)
        val persisted = profile.copy(id = "new-id")
        every { repository.save(profile) } returns Mono.just(persisted)

        val result = service.create(profile).block()!!

        assertThat(result.id).isEqualTo("new-id")
    }

    @Test
    fun `delete removes profile when it exists`() {
        val profile = ServiceProfileFixture.profile(id = "sp-del")
        every { repository.findById("sp-del") } returns Mono.just(profile)
        every { repository.deleteById("sp-del") } returns Mono.empty()

        service.delete("sp-del").block()

        verify(exactly = 1) { repository.deleteById("sp-del") }
    }

    @Test
    fun `delete throws when profile is missing`() {
        every { repository.findById("missing") } returns Mono.empty()

        assertThatThrownBy { service.delete("missing").block() }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun `findByServiceIdOrThrow returns profile when found`() {
        val profile = ServiceProfileFixture.profile(serviceId = "auth-svc")
        every { repository.findByServiceId("auth-svc") } returns
            Mono.just(profile)

        val result = service.findByServiceIdOrThrow("auth-svc").block()!!

        assertThat(result.serviceId).isEqualTo("auth-svc")
    }

    @Test
    fun `findByServiceIdOrThrow throws when not found`() {
        every { repository.findByServiceId("unknown") } returns Mono.empty()

        assertThatThrownBy { service.findByServiceIdOrThrow("unknown").block() }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun `update preserves original id from existing record`() {
        val existing = ServiceProfileFixture.profile(id = "original-id")
        val incoming = ServiceProfileFixture.profile(id = "ignored-id")
        val saved = slot<ServiceProfile>()
        every { repository.findById("original-id") } returns Mono.just(existing)
        every { repository.save(capture(saved)) } answers
            { Mono.just(saved.captured) }

        service.update("original-id", incoming).block()

        assertThat(saved.captured.id).isEqualTo("original-id")
    }

    @Test
    fun `update applies new displayName from incoming profile`() {
        val existing =
            ServiceProfileFixture.profile(
                id = "sp-1",
                displayName = "Old Name",
            )
        val incoming = ServiceProfileFixture.profile(displayName = "New Name")
        val saved = slot<ServiceProfile>()
        every { repository.findById("sp-1") } returns Mono.just(existing)
        every { repository.save(capture(saved)) } answers
            { Mono.just(saved.captured) }

        service.update("sp-1", incoming).block()

        assertThat(saved.captured.displayName).isEqualTo("New Name")
    }

    @Test
    fun `update applies new team from incoming profile`() {
        val existing =
            ServiceProfileFixture.profile(
                id = "sp-2",
                team = "platform",
            )
        val incoming = ServiceProfileFixture.profile(team = "sre")
        val saved = slot<ServiceProfile>()
        every { repository.findById("sp-2") } returns Mono.just(existing)
        every { repository.save(capture(saved)) } answers
            { Mono.just(saved.captured) }

        service.update("sp-2", incoming).block()

        assertThat(saved.captured.team).isEqualTo("sre")
    }
}
