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
import ac.shard.platform.scheduler.PlatformSchedulerFactory

class PresentationRuntime(
  private val plugin: Shard,
  private val menu: StaffMenu,
  private val animations: PunishmentAnimations,
  private val holograms: StatisticsHolograms,
) {
  fun enable() {
    plugin.server.pluginManager.registerEvents(menu, plugin)
    plugin.server.pluginManager.registerEvents(animations, plugin)
    if (!PlatformSchedulerFactory.isFolia()) {
      plugin.server.pluginManager.registerEvents(holograms, plugin)
    }
    reload()
  }

  fun reload() {
    val settings =
      runCatching { PresentationSettings.load(plugin) }
        .getOrElse {
          plugin.logger.warning(
            "Cannot load presentation.yml; keeping presentation settings: ${it.message}"
          )
          return
        }
    menu.reload(settings)
    animations.reload(settings)
    runCatching { holograms.reload(settings) }
      .onFailure {
        plugin.logger.warning(
          "Cannot load holograms.yml; keeping existing holograms: ${it.message}"
        )
      }
  }

  fun disable() {
    runCatching { animations.finishAll() }
    runCatching { menu.closeAll() }
    runCatching { holograms.disable() }
  }
}
