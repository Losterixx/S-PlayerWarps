package dev.losterixx.sPlayerWarps

import dev.losterixx.sPlayerWarps.commands.PlayerWarpCommand
import dev.losterixx.sapi.SAPI
import dev.losterixx.sapi.premade.commands.DefaultMainCommand
import dev.losterixx.sapi.utils.config.ConfigExtras
import dev.losterixx.sapi.utils.config.ConfigManager
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import dev.losterixx.sPlayerWarps.other.PWManager

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
        ConfigExtras.loadConfigFiles("data")
        ConfigExtras.loadLangFiles()
        logger.info("Loaded ${ConfigManager.getAllConfigs().size} configs!")

        // -> Load data
        PWManager.loadAllFromDisk()
        logger.info("Loaded ${PWManager.getAllWarps().size} player warps.")

        // -> Tasks
        PWManager.startSavingTask()

        // -> Register Commands & Listeners
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { commands ->
            commands.registrar().register(DefaultMainCommand.create("s-playerwarps", "s-playerwarps").build(), listOf("sPlayerWarps", "spw"))
            commands.registrar().register(PlayerWarpCommand.get().build(), listOf("pw"))
        }
        server.pluginManager.registerEvents(PlayerWarpCommand, instance)

        logger.info("Plugin has been initialized successfully!")
    }

    override fun onDisable() {
        logger.info("Plugin is shutting down...")

        // -> Cancel tasks
        PWManager.saveTask?.cancel()

        // -> Save data
        try {
            PWManager.saveAllToDisk()
            logger.info("Data has been saved.")
        } catch (e: Exception) {
            logger.warning("Error while saving player warps: ${e.message}")
        }

        logger.info("Plugin has been disabled!")
    }
}
