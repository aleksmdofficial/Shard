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
import ac.shard.scheduler.SchedulerService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.assertEquals
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.junit.jupiter.api.Test

class StaffMenuTest {
  private val plugin =
    mockk<Shard>(relaxed = true) {
      every { server.onlinePlayers } returns emptyList()
    }
  private val scheduler = mockk<SchedulerService>()
  private val menu = StaffMenu(plugin, mockk(), mockk(), mockk(), scheduler)
  private val holder = StaffMenu.Menu()
  private val player =
    mockk<Player>(relaxed = true) {
      every { hasPermission("shard.gui") } returns true
      every { openInventory.topInventory.holder } returns holder
    }
  private val click =
    mockk<InventoryClickEvent>(relaxed = true) {
      every { view.topInventory.holder } returns holder
      every { whoClicked } returns player
      every { rawSlot } returns 10
    }
  private lateinit var queued: Runnable

  init {
    menu.reload(PresentationSettings(true, true, 40, false, emptyList()))
    every { scheduler.runLater(player, any<Runnable>(), 1L) } answers
      {
        queued = secondArg()
        mockk(relaxed = true)
      }
  }

  @Test
  fun `menu locks both inventories and all drag transfers`() {
    every { click.rawSlot } returns 54
    menu.onClick(click)
    verify { click.isCancelled = true }
    verify(exactly = 0) { scheduler.runLater(player, any<Runnable>(), any<Long>()) }
    val drag =
      mockk<InventoryDragEvent>(relaxed = true) {
        every { view.topInventory.holder } returns holder
      }
    menu.onDrag(drag)
    verify { drag.isCancelled = true }
  }

  @Test
  fun `queued action cannot run after permission revocation or closing menu`() {
    var calls = 0
    holder.actions[10] = { calls++ }
    menu.onClick(click)
    every { player.hasPermission("shard.gui") } returns false
    queued.run()
    assertEquals(0, calls)
    every { player.hasPermission("shard.gui") } returns true
    menu.onClick(click)
    every { player.openInventory.topInventory.holder } returns null
    queued.run()
    assertEquals(0, calls)
  }
}
