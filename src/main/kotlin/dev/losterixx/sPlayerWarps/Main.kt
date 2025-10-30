package dev.losterixx.sPlayerWarps

import dev.losterixx.sPlayerWarps.commands.PlayerWarpCommand
import dev.losterixx.sPlayerWarps.listener.UpdateCheckerListener
import dev.losterixx.sPlayerWarps.other.Cache
import dev.losterixx.sapi.SAPI
import dev.losterixx.sapi.premade.commands.DefaultMainCommand
import dev.losterixx.sapi.utils.config.ConfigExtras
import dev.losterixx.sapi.utils.config.ConfigManager
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.plugin.java.JavaPlugin
import dev.losterixx.sPlayerWarps.other.PWManager
import dev.losterixx.sapi.utils.scheduling.SchedulerUtil
import dev.losterixx.sapi.utils.update.UpdateChecker
import dev.losterixx.utils.bStats.Metrics

class Main : JavaPlugin() {

    companion object {
        lateinit var instance: Main
            private set

        const val DEFAULT_PREFIX = "<#D1E332><b>S-PlayerWarps</b> <dark_gray>⚡ <gray>"
    }

    val miniMessage = SAPI.getMiniMessage()

    override fun onEnable() {
        logger.info("Plugin is being initialized...")

        // -> Important
        instance = this
        SAPI.init(instance)

        // -> Configs
        ConfigExtras.loadConfigFiles("data", "cache")
        ConfigManager.createConfig("mainMenu", "menus/mainMenu.yml", "menus")
        ConfigManager.createConfig("ownWarpsMenu", "menus/ownWarpsMenu.yml", "menus")
        ConfigManager.createConfig("editWarpMenu", "menus/editWarpMenu.yml", "menus")
        ConfigExtras.loadLangFiles()
        logger.info("Loaded ${ConfigManager.getAllConfigs().size} configs!")

        // -> Load data
        PWManager.loadAllFromDisk()
        logger.info("Loaded ${PWManager.getAllWarps().size} player warps.")
        logger.info("Loaded ${Cache.loadCache()} player names to cache.")

        // -> Tasks
        PWManager.startSavingTask()

        // -> Register Commands & Listeners
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { commands ->
            commands.registrar().register(DefaultMainCommand.create("s-playerwarps", "s-playerwarps").build(), listOf("sPlayerWarps", "spw"))
            commands.registrar().register(PlayerWarpCommand.get().build(), listOf("pw"))
        }
        server.pluginManager.registerEvents(PlayerWarpCommand, instance)
        server.pluginManager.registerEvents(Cache, instance)
        server.pluginManager.registerEvents(UpdateCheckerListener(), instance)

        //-> Metrics
        val metrics = Metrics(this, 27788)

        //-> Update Checker
        if (ConfigManager.getConfig("config").getBoolean("updateChecker.consoleMessage")) {
            SchedulerUtil.runAsync {
                if (!isLatestVersion()) {
                    logger.warning("You are not using the latest version of S-PlayerWarps! Please update to the latest version.")
                    logger.warning("Latest version: ${UpdateChecker.getLatestGitHubRelease("Losterixx", "S-PlayerWarps")}")
                    logger.warning("Your version: ${pluginMeta.version}")
                } else {
                    logger.info("You are using the latest version of S-PlayerWarps!")
                }
            }
        }

        logger.info("Plugin has been initialized successfully!")
    }

    override fun onDisable() {
        logger.info("Plugin is shutting down...")

        // -> Cancel tasks
        PWManager.saveTask?.cancel()

        // -> Save data
        try {
            PWManager.saveAllToDisk()
            Cache.saveCache()
            logger.info("Data has been saved.")
        } catch (e: Exception) {
            logger.warning("Error while saving player warps: ${e.message}")
        }

        logger.info("Plugin has been disabled!")
    }

    fun isLatestVersion(): Boolean {
        val currentVersion = pluginMeta.version
        val latestVersion = UpdateChecker.getLatestGitHubRelease("Losterixx", "S-PlayerWarps")
        return latestVersion != null && latestVersion == currentVersion
    }
}
