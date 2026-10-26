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
import ac.shard.database.DatabaseManager
import ac.shard.platform.scheduler.PlatformScheduler
import ac.shard.platform.scheduler.PlatformSchedulerFactory
import ac.shard.platform.scheduler.TaskHandle
import ac.shard.scheduler.SchedulerService
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import org.bukkit.ChatColor
import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.ArmorStand
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.event.world.WorldLoadEvent

@Suppress("TooManyFunctions")
class StatisticsHolograms(
  private val plugin: Shard,
  private val config: ConfigManager,
  private val database: DatabaseManager,
  private val scheduler: SchedulerService,
  private val platform: PlatformScheduler,
) : Listener {
  private data class Position(val world: UUID, val x: Double, val y: Double, val z: Double) {
    val chunkX: Int
      get() = Math.floor(x).toInt() shr CHUNK_SHIFT

    val chunkZ: Int
      get() = Math.floor(z).toInt() shr CHUNK_SHIFT
  }

  @Volatile private var positions: Map<String, Position> = emptyMap()
  @Volatile private var enabled = false
  @Volatile private var lines: List<String> = emptyList()
  @Volatile private var flags = "-"
  @Volatile private var storageAvailable = false
  private var refresh: TaskHandle? = null
  private var statistics: TaskHandle? = null

  internal fun reload(settings: PresentationSettings) {
    if (PlatformSchedulerFactory.isFolia()) {
      if (settings.holograms)
        plugin.logger.warning(
          "Statistics holograms currently require Bukkit/Paper; disabled on Folia."
        )
      return
    }
    load()
    refresh?.cancel()
    statistics?.cancel()
    enabled = settings.holograms
    lines = settings.lines
    sweepLoadedChunks()
    if (!enabled) return
    refresh = scheduler.runTimer({ refreshPositions() }, REFRESH_TICKS, REFRESH_TICKS)
    statistics =
      scheduler.runTimerAsync(
        {
          flags =
            runCatching {
                if (database.isAvailable)
                  database.database.getLogCount(System.currentTimeMillis() - DAY).toString()
                else "-"
              }
              .getOrDefault("-")
        },
        INITIAL_STATS_MILLIS,
        STATS_INTERVAL_MILLIS,
      )
  }

  @Synchronized
  @Suppress("ReturnCount")
  fun create(id: String, location: Location): String {
    if (PlatformSchedulerFactory.isFolia())
      return "Statistics holograms currently require Bukkit/Paper."
    if (!storageAvailable) return "Cannot modify holograms; fix holograms.yml and reload first."
    if (!id.matches(Regex("[a-zA-Z0-9_-]{1,32}")))
      return "Invalid ID (letters, digits, _ and -, max 32)."
    if (positions.containsKey(id)) return "That hologram already exists."
    if (positions.size >= MAX_HOLOGRAMS) return "Maximum of 64 holograms reached."
    val world = location.world ?: return "World is unavailable."
    val position = Position(world.uid, location.x, location.y, location.z)
    val replacement = positions + (id to position)
    save(replacement)
    positions = replacement
    refreshPositions()
    return if (enabled) "Created hologram: $id"
    else "Saved hologram: $id. Enable holograms in presentation.yml and /shard reload."
  }

  @Synchronized
  @Suppress("ReturnCount")
  fun delete(id: String): String {
    if (!storageAvailable) return "Cannot modify holograms; fix holograms.yml and reload first."
    val old = positions[id] ?: return "Unknown hologram: $id"
    val replacement = positions - id
    save(replacement)
    positions = replacement
    scheduleChunk(old)
    return "Deleted hologram: $id"
  }

  fun list(): String = positions.keys.sorted().joinToString(", ").ifEmpty { "No holograms." }

  private fun load() {
    storageAvailable = false
    val file = plugin.dataFolder.resolve("holograms.yml")
    val yaml = YamlConfiguration()
    // Fail rather than silently replacing malformed storage on the next create/delete.
    if (file.exists()) yaml.load(file)
    val ids = yaml.getConfigurationSection("locations")?.getKeys(false).orEmpty()
    require(ids.size <= MAX_HOLOGRAMS) { "Maximum of 64 holograms exceeded" }
    positions = ids.associate { id ->
      require(id.matches(Regex("[a-zA-Z0-9_-]{1,32}"))) { "Invalid hologram ID: $id" }
      val section = requireNotNull(yaml.getConfigurationSection("locations.$id"))
      val world = UUID.fromString(section.getString("world"))
      val x = section.getDouble("x")
      val y = section.getDouble("y")
      val z = section.getDouble("z")
      require(x.isFinite() && y.isFinite() && z.isFinite()) { "Non-finite position: $id" }
      id to Position(world, x, y, z)
    }
    storageAvailable = true
  }

  private fun save(replacement: Map<String, Position>) {
    val yaml = YamlConfiguration()
    replacement.forEach { (id, p) ->
      yaml.set("locations.$id.world", p.world.toString())
      yaml.set("locations.$id.x", p.x)
      yaml.set("locations.$id.y", p.y)
      yaml.set("locations.$id.z", p.z)
    }
    val target = plugin.dataFolder.resolve("holograms.yml").toPath()
    val temporary = Files.createTempFile(plugin.dataFolder.toPath(), "holograms-", ".tmp")
    try {
      Files.writeString(temporary, yaml.saveToString())
      Files.move(
        temporary,
        target,
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING,
      )
    } finally {
      Files.deleteIfExists(temporary)
    }
  }

  private fun refreshPositions() {
    positions.values.distinctBy { Triple(it.world, it.chunkX, it.chunkZ) }.forEach(::scheduleChunk)
  }

  private fun scheduleChunk(position: Position) {
    val world = plugin.server.getWorld(position.world) ?: return
    platform.regionScheduler.execute(plugin, world, position.chunkX, position.chunkZ) {
      if (world.isChunkLoaded(position.chunkX, position.chunkZ)) {
        render(world.getChunkAt(position.chunkX, position.chunkZ))
      }
    }
  }

  private fun sweepLoadedChunks() {
    plugin.server.worlds.forEach { world ->
      world.loadedChunks.forEach { chunk ->
        platform.regionScheduler.execute(plugin, world, chunk.x, chunk.z) {
          if (world.isChunkLoaded(chunk.x, chunk.z)) render(chunk)
        }
      }
    }
  }

  private fun render(chunk: Chunk) {
    val desired =
      if (enabled)
        positions.filterValues {
          it.world == chunk.world.uid && it.chunkX == chunk.x && it.chunkZ == chunk.z
        }
      else emptyMap()
    val existing =
      chunk.entities
        .filterIsInstance<ArmorStand>()
        .filter { TAG in it.scoreboardTags }
        .toMutableList()
    desired.forEach { (id, position) ->
      lines.forEachIndexed { index, template ->
        val key = "$TAG:$id:$index"
        val location = Location(chunk.world, position.x, position.y - index * LINE_GAP, position.z)
        val stand =
          existing.firstOrNull { key in it.scoreboardTags }?.also { existing.remove(it) }
            ?: chunk.world.spawn(location, ArmorStand::class.java) { entity ->
              entity.isVisible = false
              entity.isMarker = true
              entity.setGravity(false)
              entity.isInvulnerable = true
              entity.isSilent = true
              entity.isPersistent = false
              entity.isCustomNameVisible = true
              entity.addScoreboardTag(TAG)
              entity.addScoreboardTag(key)
            }
        stand.customName =
          ChatColor.translateAlternateColorCodes(
            '&',
            template
              .replace("{online}", plugin.server.onlinePlayers.size.toString())
              .replace("{model}", config.modelTitle())
              .replace("{flags_24h}", flags),
          )
      }
    }
    existing.forEach { it.remove() }
  }

  @EventHandler fun onLoad(event: ChunkLoadEvent) = render(event.chunk)

  @EventHandler
  fun onUnload(event: ChunkUnloadEvent) {
    removeOwned(event.chunk)
  }

  @EventHandler
  fun onWorldLoad(event: WorldLoadEvent) {
    positions.values.filter { it.world == event.world.uid }.forEach(::scheduleChunk)
  }

  fun disable() {
    enabled = false
    refresh?.cancel()
    statistics?.cancel()
    if (!PlatformSchedulerFactory.isFolia()) {
      plugin.server.worlds.forEach { world ->
        world.loadedChunks.forEach(::removeOwned)
      }
    }
  }

  private companion object {
    const val TAG = "shard:statistics-hologram"
    const val DAY = 86_400_000L
    const val CHUNK_SHIFT = 4
    const val REFRESH_TICKS = 100L
    const val INITIAL_STATS_MILLIS = 1_000L
    const val STATS_INTERVAL_MILLIS = 30_000L
    const val MAX_HOLOGRAMS = 64
    const val LINE_GAP = 0.27
  }

  private fun removeOwned(chunk: Chunk) {
    chunk.entities.filter { TAG in it.scoreboardTags }.forEach { it.remove() }
  }
}
