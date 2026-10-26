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
import ac.shard.config.ConfigManager
import ac.shard.detection.SuspicionPolicy
import ac.shard.player.PlayerDataManager
import ac.shard.scheduler.SchedulerService
import java.util.Locale
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

// Slot numbers describe a fixed six-row inventory layout.
@Suppress("TooManyFunctions", "MagicNumber")
class StaffMenu(
  private val plugin: Shard,
  private val config: ConfigManager,
  private val players: PlayerDataManager,
  private val suspicion: SuspicionPolicy,
  private val scheduler: SchedulerService,
) : Listener {
  @Volatile private var enabled = false

  internal class Menu : InventoryHolder {
    lateinit var contents: Inventory
    val actions = mutableMapOf<Int, (Player) -> Unit>()

    override fun getInventory(): Inventory = contents
  }

  internal fun reload(settings: PresentationSettings) {
    enabled = settings.gui
    closeAll()
  }

  private fun text(english: String, russian: String): String =
    if (config.config.getString("locale", "en").equals("ru", true)) russian else english

  fun open(player: Player) {
    if (!enabled) {
      player.sendMessage(
        text("§cStaff GUI is disabled in presentation.yml.", "§cМеню отключено в presentation.yml.")
      )
      return
    }
    if (!player.hasPermission("shard.gui")) return
    val menu = create(text("Shard • Control panel", "Shard • Управление"))
    button(menu, 10, Material.PLAYER_HEAD, text("Online players", "Онлайн-игроки")) {
      list(it, false, 0)
    }
    button(menu, 12, Material.ENDER_EYE, text("Suspicious players", "Подозрительные игроки")) {
      list(it, true, 0)
    }
    command(menu, 14, Material.BOOK, text("Recent violations", "Последние нарушения"), "logs")
    command(menu, 16, Material.COMPARATOR, text("Statistics", "Статистика"), "stats")
    command(
      menu,
      28,
      Material.NOTE_BLOCK,
      text("Toggle alerts", "Переключить уведомления"),
      "alerts",
    )
    command(
      menu,
      30,
      Material.COMPASS,
      text("Monitor settings", "Настройки монитора"),
      "monitor settings",
    )
    command(
      menu,
      32,
      Material.NAME_TAG,
      text("Toggle nametags", "Переключить метки игроков"),
      "view",
    )
    command(
      menu,
      34,
      Material.REDSTONE,
      text("Reload configuration", "Перезагрузить конфигурацию"),
      "reload",
    )
    player.openInventory(menu.contents)
  }

  private fun list(player: Player, suspiciousOnly: Boolean, requestedPage: Int) {
    if (!allowed(player, "shard.profile")) return
    if (suspiciousOnly && !allowed(player, "shard.suspicious.list")) return
    val entries =
      players
        .getPlayers()
        .filter { !suspiciousOnly || suspicion.isSuspicious(it) }
        .sortedBy { it.name.lowercase(Locale.ROOT) }
    val page = requestedPage.coerceIn(0, ((entries.size - 1).coerceAtLeast(0) / PAGE_SIZE))
    val title =
      if (suspiciousOnly) text("Suspicious", "Подозрительные") else text("Players", "Игроки")
    val menu = create("Shard • $title • ${page + 1}")
    entries.drop(page * PAGE_SIZE).take(PAGE_SIZE).forEachIndexed { index, entry ->
      val uuid = entry.uuid
      val buffer = String.format(Locale.US, "%.2f", entry.detection.buffer)
      button(menu, index, Material.PLAYER_HEAD, entry.name, listOf("AI buffer: $buffer")) { viewer
        ->
        val target = plugin.server.getPlayer(uuid)
        if (target != null && allowed(viewer, "shard.profile")) profile(viewer, target)
      }
    }
    button(menu, 45, Material.ARROW, text("Previous", "Назад")) {
      list(it, suspiciousOnly, page - 1)
    }
    button(menu, 49, Material.NETHER_STAR, text("Main menu", "Главное меню")) { open(it) }
    button(menu, 53, Material.ARROW, text("Next", "Вперёд")) { list(it, suspiciousOnly, page + 1) }
    player.openInventory(menu.contents)
  }

  private fun profile(viewer: Player, target: Player) {
    val menu = create("Shard • ${target.name}")
    command(menu, 10, Material.BOOK, text("Profile", "Профиль"), "profile ${target.name}")
    command(menu, 12, Material.PAPER, text("History", "История"), "history ${target.name}")
    command(menu, 14, Material.COMPASS, text("Monitor", "Наблюдать"), "monitor ${target.name}")
    button(menu, 49, Material.ARROW, text("Main menu", "Главное меню")) { open(it) }
    viewer.openInventory(menu.contents)
  }

  private fun allowed(player: Player, permission: String): Boolean {
    if (player.hasPermission(permission)) return true
    player.sendMessage(text("§cYou do not have permission.", "§cНедостаточно прав."))
    return false
  }

  private fun create(title: String): Menu {
    val menu = Menu()
    menu.contents = Bukkit.createInventory(menu, 54, title)
    return menu
  }

  @Suppress("LongParameterList")
  private fun button(
    menu: Menu,
    slot: Int,
    material: Material,
    label: String,
    lore: List<String> = emptyList(),
    action: (Player) -> Unit,
  ) {
    val item = ItemStack(material)
    val meta = item.itemMeta ?: return
    meta.setDisplayName("§b$label")
    meta.lore = lore
    item.itemMeta = meta
    menu.contents.setItem(slot, item)
    menu.actions[slot] = action
  }

  private fun command(menu: Menu, slot: Int, material: Material, label: String, command: String) {
    button(menu, slot, material, label) { player ->
      player.closeInventory()
      // Execute as the viewer so the command framework enforces all existing permissions.
      player.performCommand("shard $command")
    }
  }

  @EventHandler
  @Suppress("ReturnCount")
  fun onClick(event: InventoryClickEvent) {
    val menu = event.view.topInventory.holder as? Menu ?: return
    event.isCancelled = true
    val player = event.whoClicked as? Player ?: return
    if (!enabled || !player.hasPermission("shard.gui")) return
    val action = menu.actions[event.rawSlot] ?: return
    scheduler.runLater(
      player,
      {
        if (
          enabled &&
            player.hasPermission("shard.gui") &&
            player.openInventory.topInventory.holder === menu
        ) {
          action(player)
        }
      },
      1,
    )
  }

  @EventHandler
  fun onDrag(event: InventoryDragEvent) {
    if (event.view.topInventory.holder is Menu) event.isCancelled = true
  }

  fun closeAll() {
    plugin.server.onlinePlayers.forEach { player ->
      scheduler.runSync(player) {
        if (player.openInventory.topInventory.holder is Menu) player.closeInventory()
      }
    }
  }

  private companion object {
    const val PAGE_SIZE = 45
  }
}
