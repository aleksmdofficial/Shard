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

import ac.shard.command.ShardCommand
import ac.shard.command.shardCommand
import ac.shard.scheduler.SchedulerService
import ac.shard.sender.Sender
import org.bukkit.entity.Player
import org.incendo.cloud.CommandManager
import org.incendo.cloud.parser.standard.StringParser

class PresentationCommand(
  private val menu: StaffMenu,
  private val holograms: StatisticsHolograms,
  private val animations: PunishmentAnimations,
  private val scheduler: SchedulerService,
) : ShardCommand {
  override fun register(manager: CommandManager<Sender>) {
    manager.shardCommand {
      literal("gui").permission("shard.gui").handler { context ->
        val player = context.sender().nativeSender as? Player
        if (player == null) context.sender().nativeSender.sendMessage("Run this command in game.")
        else scheduler.runSync(player) { menu.open(player) }
      }
    }
    manager.shardCommand {
      literal("hologram").literal("list").permission("shard.hologram.manage").handler {
        it.sender().nativeSender.sendMessage(holograms.list())
      }
    }
    manager.shardCommand {
      literal("hologram")
        .literal("create")
        .permission("shard.hologram.manage")
        .required("id", StringParser.stringParser())
        .handler { context ->
          val player = context.sender().nativeSender as? Player
          val id: String = context["id"]
          if (player == null) context.sender().nativeSender.sendMessage("Run this command in game.")
          else
            scheduler.runSync(player) {
              player.sendMessage(holograms.create(id, player.location.add(0.0, 2.0, 0.0)))
            }
        }
    }
    manager.shardCommand {
      literal("hologram")
        .literal("delete")
        .permission("shard.hologram.manage")
        .required("id", StringParser.stringParser())
        .handler { context ->
          val id: String = context["id"]
          scheduler.runSync { context.sender().nativeSender.sendMessage(holograms.delete(id)) }
        }
    }
    manager.shardCommand {
      literal("animation")
        .permission("shard.animation.preview")
        .required("style", StringParser.stringParser())
        .handler { context ->
          val player = context.sender().nativeSender as? Player
          val style = AnimationStyle.parse(context["style"])
          if (player == null) context.sender().nativeSender.sendMessage("Run this command in game.")
          else if (style == null) player.sendMessage("Styles: sparks, lightning")
          else animations.play(player, style) {}
        }
    }
  }
}
