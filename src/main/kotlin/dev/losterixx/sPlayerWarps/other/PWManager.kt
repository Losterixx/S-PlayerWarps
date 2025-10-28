package dev.losterixx.sPlayerWarps.other

import dev.losterixx.sapi.utils.config.ConfigManager
import dev.losterixx.sPlayerWarps.Main
import org.bukkit.Location
import java.util.UUID

object PWManager {

    private val main = Main.instance
    private val data get() = ConfigManager.getConfig("data")

    fun getAllWarps(): List<PlayerWarp> {
        val warpsSection = data.getSection("warps") ?: return emptyList()

        val result = mutableListOf<PlayerWarp>()
        for (key in warpsSection.getRoutesAsStrings(false)) {
            val sub = getPlayerWarp(key)
            if (sub != null) result.add(sub)
        }
        return result
    }

    fun savePlayerWarp(playerWarp: PlayerWarp) {
        if (data.getSection("warps") == null) data.createSection("warps")

        val (identifier, owner, displayName, location) = playerWarp

        val worldName = location.world?.name ?: return
        if (data.getSection("warps.$identifier") == null) data.createSection("warps.$identifier")

        data.apply {
            set("warps.$identifier.owner", owner.toString())
            set("warps.$identifier.displayName", displayName)
            set("warps.$identifier.loc.world", worldName)
            set("warps.$identifier.loc.x", location.x)
            set("warps.$identifier.loc.y", location.y)
            set("warps.$identifier.loc.z", location.z)
            set("warps.$identifier.loc.yaw", location.yaw)
            set("warps.$identifier.loc.pitch", location.pitch)
            save()
        }
    }

    fun getPlayerWarp(identifier: String): PlayerWarp? {
        val warpSection = data.getSection("warps.$identifier") ?: return null

        val owner = UUID.fromString(warpSection.getString("owner") ?: return null)
        val displayName = warpSection.getString("displayName")

        val locSection = warpSection.getSection("loc") ?: return null
        val worldName = locSection.getString("world") ?: return null
        val x = locSection.getDouble("x")
        val y = locSection.getDouble("y")
        val z = locSection.getDouble("z")
        val yaw = locSection.getDouble("yaw").toFloat()
        val pitch = locSection.getDouble("pitch").toFloat()

        val world = main.server.getWorld(worldName) ?: return null
        val location = Location(world, x, y, z, yaw, pitch)

        return PlayerWarp(identifier, owner, displayName, location)
    }

    fun existsPlayerWarp(identifier: String): Boolean {
        return data.getSection("warps.$identifier") != null
    }

    fun getPlayerWarpsByOwner(owner: UUID): List<PlayerWarp> {
        val warpsSection = data.getSection("warps") ?: return emptyList()

        val result = mutableListOf<PlayerWarp>()
        for (key in warpsSection.getRoutesAsStrings(false)) {
            val warp = getPlayerWarp(key)
            if (warp != null && warp.owner == owner) {
                result.add(warp)
            }
        }
        return result
    }

}