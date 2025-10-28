package dev.losterixx.sPlayerWarps.other

import dev.losterixx.sapi.utils.config.ConfigManager
import dev.losterixx.sPlayerWarps.Main
import dev.losterixx.sPlayerWarps.Main.Companion.instance
import org.bukkit.Location
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PWManager {

    private val main = Main.instance
    private val config get() = ConfigManager.getConfig("config")
    private val data get() = ConfigManager.getConfig("data")
    private val cache: MutableMap<String, PlayerWarp> = ConcurrentHashMap()
    var saveTask: BukkitTask? = null
        private set

    fun loadAllFromDisk() {
        cache.clear()
        val warpsSection = data.getSection("warps") ?: return

        for (key in warpsSection.getRoutesAsStrings(false)) {
            val warpSection = warpsSection.getSection(key) ?: continue

            val owner = runCatching { UUID.fromString(warpSection.getString("owner") ?: continue) }.getOrNull() ?: continue
            val displayName = warpSection.getString("displayName")

            val locSection = warpSection.getSection("loc") ?: continue
            val worldName = locSection.getString("world") ?: continue
            val x = locSection.getDouble("x")
            val y = locSection.getDouble("y")
            val z = locSection.getDouble("z")
            val yaw = locSection.getDouble("yaw").toFloat()
            val pitch = locSection.getDouble("pitch").toFloat()

            val world = main.server.getWorld(worldName) ?: continue
            val location = Location(world, x, y, z, yaw, pitch)

            cache[key] = PlayerWarp(key, owner, displayName, location)
        }
    }

    fun saveAllToDisk() {
        val snapshot = cache.toMap()
        val data = ConfigManager.getConfig("data") ?: return

        data.remove("warps")

        if (snapshot.isEmpty()) {
            data.save()
            return
        }

        for ((identifier, playerWarp) in snapshot) {
            val (id, owner, displayName, location) = playerWarp
            val worldName = location.world?.name ?: continue

            if (data.getSection("warps.$identifier") == null) data.createSection("warps.$identifier")
            data.set("warps.$identifier.owner", owner.toString())
            data.set("warps.$identifier.displayName", displayName)
            data.set("warps.$identifier.loc.world", worldName)
            data.set("warps.$identifier.loc.x", location.x)
            data.set("warps.$identifier.loc.y", location.y)
            data.set("warps.$identifier.loc.z", location.z)
            data.set("warps.$identifier.loc.yaw", location.yaw)
            data.set("warps.$identifier.loc.pitch", location.pitch)
        }

        data.save()
    }

    fun startSavingTask() {
        val config = ConfigManager.getConfig("config")
        val delaySeconds = config.getInt("saving.delay", 1200)
        val initialDelayTicks = (3 + delaySeconds) * 20L
        val periodTicks = delaySeconds * 20L

        saveTask = if (config.getBoolean("saving.async", true)) {
            main.server.scheduler.runTaskTimerAsynchronously(instance, Runnable {
                try {
                    saveAllToDisk()
                } catch (e: Exception) {
                    main.logger.warning("Error while saving player warps async: ${e.message}")
                }
            }, initialDelayTicks, periodTicks)
        } else {
            main.server.scheduler.runTaskTimer(instance, Runnable {
                try {
                    saveAllToDisk()
                } catch (e: Exception) {
                    main.logger.warning("Error while saving player warps: ${e.message}")
                }
            }, initialDelayTicks, periodTicks)
        }
    }

    fun getAllWarps(): List<PlayerWarp> {
        return cache.values.toList()
    }

    fun addPlayerWarp(playerWarp: PlayerWarp) {
        cache[playerWarp.identifier] = playerWarp
    }

    fun removePlayerWarp(playerWarp: PlayerWarp) {
        cache.remove(playerWarp.identifier)
    }

    fun getPlayerWarp(identifier: String): PlayerWarp? {
        return cache[identifier]
    }

    fun existsPlayerWarp(identifier: String): Boolean {
        return cache.containsKey(identifier)
    }

    fun getPlayerWarpsByOwner(owner: UUID): List<PlayerWarp> {
        return cache.values.filter { it.owner == owner }
    }

}