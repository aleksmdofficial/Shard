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
import ac.shard.platform.scheduler.PlatformScheduler
import ac.shard.scheduler.SchedulerService
import io.mockk.every
import io.mockk.mockk
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.bukkit.Location
import org.bukkit.World
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class StatisticsHologramsTest {
  @TempDir lateinit var directory: Path

  private fun service(): StatisticsHolograms {
    val plugin =
      mockk<Shard>(relaxed = true) {
        every { dataFolder } returns directory.toFile()
        every { server.worlds } returns emptyList()
      }
    val scheduler = mockk<SchedulerService>(relaxed = true)
    val platform = mockk<PlatformScheduler>(relaxed = true)
    return StatisticsHolograms(
        plugin,
        mockk(relaxed = true),
        mockk(relaxed = true),
        scheduler,
        platform,
      )
      .also { it.reload(PresentationSettings(true, true, 40, false, emptyList())) }
  }

  @Test
  fun `locations survive a restart and deletion persists`() {
    val world = mockk<World> { every { uid } returns UUID.randomUUID() }
    val original = service()
    original.create("spawn", Location(world, -0.5, 72.0, 16.0))
    val restarted = service()
    assertEquals("spawn", restarted.list())
    assertTrue(restarted.create("spawn", Location(world, 1.0, 2.0, 3.0)).contains("already exists"))
    restarted.delete("spawn")
    assertEquals("No holograms.", service().list())
  }

  @Test
  fun `invalid IDs cannot create nested configuration paths`() {
    val holograms = service()
    assertTrue(
      holograms.create("../../other", Location(null, 0.0, 0.0, 0.0)).startsWith("Invalid ID")
    )
    assertFalse(directory.resolve("holograms.yml").toFile().exists())
  }

  @Test
  fun `malformed storage is not overwritten by later create commands`() {
    val holograms = service()
    val file = directory.resolve("holograms.yml").toFile()
    val broken = "locations: [unterminated"
    file.writeText(broken)
    assertFails { holograms.reload(PresentationSettings(true, true, 40, false, emptyList())) }
    assertTrue(holograms.create("spawn", Location(null, 0.0, 0.0, 0.0)).startsWith("Cannot modify"))
    assertEquals(broken, file.readText())
  }
}
