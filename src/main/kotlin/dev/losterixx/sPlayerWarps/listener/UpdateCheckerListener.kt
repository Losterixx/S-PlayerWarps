package dev.losterixx.sPlayerWarps.listener

import dev.losterixx.sPlayerWarps.Main
import dev.losterixx.sapi.utils.config.ConfigManager
import dev.losterixx.sapi.utils.scheduling.SchedulerUtil
import dev.losterixx.sapi.utils.update.UpdateChecker
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class UpdateCheckerListener : Listener {

    private val main = Main.instance
    private val mm = main.miniMessage
    private val config get() = ConfigManager.getConfig("config")
    private val prefix get() = config.getString("prefix") ?: Main.DEFAULT_PREFIX
    private val messages get() = ConfigManager.getConfig(config.getString("langFile", "english"))

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        if (!config.getBoolean("updateChecker.playerMessage")) return
        if (!player.hasPermission("s-playerwarps.admin.notifyUpdate")) return

        SchedulerUtil.runAsync {
            val currentVersion = main.description.version
            val latestVersion = UpdateChecker.getLatestGitHubRelease("Losterixx", "S-PlayerWarps")

            if (latestVersion != null && latestVersion != currentVersion) {
                event.player.sendMessage(mm.deserialize(prefix + messages.getString("other.updateChecker")
                    .replace("%latest-version%", latestVersion)
                    .replace("%current-version%", currentVersion)))
            }
        }
    }

}