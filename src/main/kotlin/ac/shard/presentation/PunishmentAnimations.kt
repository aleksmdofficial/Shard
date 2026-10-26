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
import ac.shard.platform.scheduler.TaskHandle
import ac.shard.scheduler.SchedulerService
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

class PunishmentAnimations(
  private val plugin: Shard,
  private val scheduler: SchedulerService,
  private val platform: PlatformScheduler,
) : Listener {
  @Volatile private var enabled = false
  @Volatile private var duration = DEFAULT_ANIMATION_TICKS
  private val pending = ConcurrentHashMap<UUID, Completion>()
  private val tasks = ConcurrentHashMap<Completion, TaskHandle>()

  internal fun reload(settings: PresentationSettings) {
    enabled = settings.animations
    duration = settings.duration
    if (!enabled) finishAll()
  }

  // An effect failure must never prevent the next punishment action.
  @Suppress("TooGenericExceptionCaught")
  fun play(player: Player, style: AnimationStyle, next: () -> Unit) {
    if (!enabled || !plugin.isEnabled || !player.isOnline) {
      next()
      return
    }
    lateinit var done: Completion
    done = Completion {
      tasks.remove(done)?.cancel()
      pending.remove(player.uniqueId, done)
      next()
    }
    // Another punishment sequence must not replace this one's continuation.
    if (pending.putIfAbsent(player.uniqueId, done) != null) {
      next()
      return
    }
    try {
      // Global timeout also completes if the player retires during scheduling.
      val timeout = scheduler.runLater({ done.finish() }, duration)
      tasks[done] = timeout
      if (pending[player.uniqueId] !== done) tasks.remove(done)?.cancel()
      platform.entityScheduler.run(
        player,
        plugin,
        Runnable {
          if (pending[player.uniqueId] !== done || !player.isOnline) return@Runnable
          runCatching {
              val location = player.location.add(0.0, 1.0, 0.0)
              if (style == AnimationStyle.LIGHTNING) {
                player.world.strikeLightningEffect(player.location)
              } else {
                player.world.spawnParticle(
                  Particle.END_ROD,
                  location,
                  PARTICLE_COUNT,
                  SPREAD_XZ,
                  SPREAD_Y,
                  SPREAD_XZ,
                  SPEED,
                )
                player.world.playSound(location, Sound.BLOCK_BEACON_ACTIVATE, VOLUME, PITCH)
              }
            }
            .onFailure {
              plugin.logger.warning("Punishment animation failed: ${it.message}")
              done.finish()
            }
        },
        Runnable { done.finish() },
      ) ?: done.finish()
    } catch (exception: Exception) {
      plugin.logger.warning("Cannot schedule punishment animation: ${exception.message}")
      done.finish()
    }
  }

  @EventHandler
  fun onQuit(event: PlayerQuitEvent) {
    pending[event.player.uniqueId]?.finish()
  }

  fun finishAll() {
    pending.values.toList().forEach { it.finish() }
  }

  private companion object {
    const val PARTICLE_COUNT = 40
    const val SPREAD_XZ = 0.5
    const val SPREAD_Y = 0.8
    const val SPEED = 0.02
    const val VOLUME = 0.6f
    const val PITCH = 1.2f
  }
}
