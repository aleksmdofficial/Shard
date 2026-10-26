/*
 * This file is part of Shard - https://github.com/KaelusAI/Shard
 * Copyright (C) 2026 KaelusAI
 *
 * Shard is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Shard is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package ac.shard.presentation

import ac.shard.Shard
import ac.shard.platform.scheduler.EntityScheduler
import ac.shard.platform.scheduler.PlatformScheduler
import ac.shard.platform.scheduler.TaskHandle
import ac.shard.scheduler.SchedulerService
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerQuitEvent
import org.junit.jupiter.api.Test

class PunishmentAnimationsTest {
  private val player =
    mockk<Player>(relaxed = true) {
      every { uniqueId } returns UUID.randomUUID()
      every { isOnline } returns true
    }
  private val plugin = mockk<Shard>(relaxed = true) { every { isEnabled } returns true }
  private val scheduler = mockk<SchedulerService>()
  private val entity = mockk<EntityScheduler>()
  private val platform = mockk<PlatformScheduler> { every { entityScheduler } returns entity }
  private val timeouts = mutableListOf<Runnable>()
  private val animations = PunishmentAnimations(plugin, scheduler, platform)

  init {
    every { scheduler.runLater(any<Runnable>(), any<Long>()) } answers
      {
        timeouts += firstArg<Runnable>()
        mockk<TaskHandle>(relaxed = true)
      }
    every { entity.run(player, plugin, any(), any()) } returns mockk(relaxed = true)
    animations.reload(PresentationSettings(true, true, 40, false, emptyList()))
  }

  @Test
  fun `disconnect and late timeout do not duplicate the next action`() {
    val next = AtomicInteger()
    animations.play(player, AnimationStyle.SPARKS) { next.incrementAndGet() }
    assertEquals(0, next.get())
    animations.onQuit(PlayerQuitEvent(player, ""))
    timeouts.single().run()
    animations.finishAll()
    assertEquals(1, next.get())
  }

  @Test
  fun `disabling effects releases pending punishments`() {
    val next = AtomicInteger()
    animations.play(player, AnimationStyle.SPARKS) { next.incrementAndGet() }
    animations.reload(PresentationSettings(true, false, 40, false, emptyList()))
    timeouts.single().run()
    animations.play(player, AnimationStyle.SPARKS) { next.incrementAndGet() }
    assertEquals(2, next.get())
  }

  @Test
  fun `retired entity does not swallow the punishment`() {
    every { entity.run(player, plugin, any(), any()) } returns null
    val next = AtomicInteger()
    animations.play(player, AnimationStyle.LIGHTNING) { next.incrementAndGet() }
    timeouts.single().run()
    assertEquals(1, next.get())
  }

  @Test
  fun `concurrent effects retain both continuations`() {
    val first = AtomicInteger()
    val second = AtomicInteger()
    animations.play(player, AnimationStyle.SPARKS) { first.incrementAndGet() }
    animations.play(player, AnimationStyle.SPARKS) { second.incrementAndGet() }
    assertEquals(0, first.get())
    assertEquals(1, second.get())
    timeouts.single().run()
    assertEquals(1, first.get())
  }
}
