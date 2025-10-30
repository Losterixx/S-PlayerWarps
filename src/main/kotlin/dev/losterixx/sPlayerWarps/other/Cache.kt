package dev.losterixx.sPlayerWarps.other

import dev.losterixx.sPlayerWarps.Main
import dev.losterixx.sapi.utils.config.ConfigManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.util.UUID

object Cache : Listener {

    private val main = Main.instance
    private val cache get() = ConfigManager.getConfig("cache")
    val nameCache = mutableMapOf<UUID, String>()

    fun loadCache(): Int {
        val cfg = cache
        val playersSection = try { cfg.getSection("players") } catch (e: Exception) { null }
        if (playersSection == null) return 0

        var count = 0

        for (key in playersSection.getRoutesAsStrings(false)) {
            val name = cfg.getString("players.$key.name") ?: continue
            try {
                val uuid = UUID.fromString(key)
                nameCache[uuid] = name
                count++
            } catch (_: Exception) { }
        }

        return count
    }

    fun saveCache() {
        cache.remove("players")
        for ((uuid, name) in nameCache) {
            cache.set("players.${uuid}.name", name)
        }
        ConfigManager.saveConfig("cache")
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val uuid = player.uniqueId
        val name = player.name

        val previous = nameCache[uuid]
        if (previous == null || previous != name) {
            nameCache[uuid] = name
        }
    }

}