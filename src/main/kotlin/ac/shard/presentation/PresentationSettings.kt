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
import java.util.Locale
import org.bukkit.configuration.file.YamlConfiguration

internal const val DEFAULT_ANIMATION_TICKS = 40L
internal const val MAX_ANIMATION_TICKS = 100L
internal const val MAX_HOLOGRAM_LINES = 12
private const val MAX_LINE_LENGTH = 256

internal data class PresentationSettings(
  val gui: Boolean,
  val animations: Boolean,
  val duration: Long,
  val holograms: Boolean,
  val lines: List<String>,
) {
  companion object {
    fun load(plugin: Shard): PresentationSettings {
      val file = plugin.dataFolder.resolve("presentation.yml")
      if (!file.exists()) plugin.saveResource("presentation.yml", false)
      val yaml = YamlConfiguration().apply { load(file) }
      return PresentationSettings(
        yaml.getBoolean("gui.enabled", true),
        yaml.getBoolean("animations.enabled", true),
        yaml
          .getLong("animations.duration-ticks", DEFAULT_ANIMATION_TICKS)
          .coerceIn(1, MAX_ANIMATION_TICKS),
        yaml.getBoolean("holograms.enabled", false),
        yaml.getStringList("holograms.lines").take(MAX_HOLOGRAM_LINES).map {
          it.take(MAX_LINE_LENGTH)
        },
      )
    }
  }
}

enum class AnimationStyle {
  SPARKS,
  LIGHTNING;

  companion object {
    fun parse(value: String): AnimationStyle? = entries.firstOrNull {
      it.name == value.trim().uppercase(Locale.ROOT)
    }
  }
}
