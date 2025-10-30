package dev.losterixx.sPlayerWarps.commands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import dev.losterixx.sapi.utils.config.ConfigManager
import dev.losterixx.sPlayerWarps.Main
import dev.losterixx.sPlayerWarps.other.ui.GuiManager
import dev.losterixx.sPlayerWarps.other.PWManager
import dev.losterixx.sPlayerWarps.other.PlayerWarp
import dev.triumphteam.gui.guis.Gui
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.CompletableFuture

object PlayerWarpCommand : Listener {

    private val main = Main.instance
    private val mm = main.miniMessage
    private val config get() = ConfigManager.getConfig("config")
    private val prefix get() = config.getString("prefix") ?: Main.DEFAULT_PREFIX
    private val messages get() = ConfigManager.getConfig(config.getString("langFile", "english"))

    private val pendingTeleports = mutableMapOf<UUID, Pair<BukkitTask, Location>>()

    private fun normalizeSoundKey(soundName: String): String = soundName.lowercase() //.replace('_', '.')

    fun get(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("playerwarp")
            .requires { ctx -> ctx.sender is Player && ctx.sender.hasPermission("s-playerwarps.command.playerwarp.use") }
            .executes { ctx ->
                val sender = ctx.source.sender as Player

                if (sender.hasPermission("s-playerwarps.command.playerwarp.menu")) {
                    sender.performCommand("playerwarp menu")
                } else {
                    sender.sendMessage(mm.deserialize(prefix + messages.getString("commands.playerwarp.usage")))
                }

                return@executes 1
            }
            .then(Commands.literal("help")
                .requires { ctx -> ctx.sender.hasPermission("s-playerwarps.command.playerwarp.help") }
                .executes { ctx ->
                    val sender = ctx.source.sender as Player

                    sender.sendMessage(mm.deserialize(prefix + messages.getString("commands.playerwarp.usage")))

                    return@executes 1
                }
            )
            .then(Commands.literal("create")
                .requires { ctx -> ctx.sender.hasPermission("s-playerwarps.command.playerwarp.create") }
                .then(Commands.argument("identifier", StringArgumentType.string())
                    .executes { ctx ->
                        val sender = ctx.source.sender as Player

                        if (PWManager.getPlayerWarpsByOwner(sender.uniqueId).size >= config.getInt("playerwarps.maxWarpsPerPlayer", 5)) {
                            sender.sendMessage(mm.deserialize(prefix + messages.getString("commands.playerwarp.create.maxWarpsReached")))
                            return@executes 1
                        }

                        val identifier = StringArgumentType.getString(ctx, "identifier")

                        val identifierRegex = Regex(config.getString("playerwarps.identifier.regex", "^[A-Za-z0-9_]+$"))
                        val minLen = config.getInt("playerwarps.identifier.minLength", 4)
                        val maxLen = config.getInt("playerwarps.identifier.maxLength", 16)

                        if (!identifierRegex.matches(identifier)) {
                            sender.sendMessage(mm.deserialize(prefix + messages.getString("commands.playerwarp.create.identifierRegex")
                                .replace("%warp%", identifier)))
                            return@executes 1
                        }

                        if (identifier.length !in minLen..maxLen) {
                            sender.sendMessage(mm.deserialize(prefix + messages.getString("commands.playerwarp.create.identifierLength")
                                .replace("%warp%", identifier)))
                            return@executes 1
                        }

                        if (PWManager.existsPlayerWarp(identifier)) {
                            sender.sendMessage(mm.deserialize(prefix + messages.getString("commands.playerwarp.create.alreadyExists")
                                .replace("%warp%", identifier)))
                            return@executes 1
                        }

                        PWManager.addPlayerWarp(
                            PlayerWarp(identifier, sender.uniqueId, sender.location, material = Material.getMaterial(config.getString("playerwarps.defaultWarpIcon")) ?: Material.COMPASS)
                        )

                        sender.sendMessage(mm.deserialize(prefix + messages.getString("commands.playerwarp.create.success")
                            .replace("%warp%", identifier)))

                        return@executes 1
                    }
                )
            )
            .then(Commands.literal("teleport")
                .requires { ctx -> ctx.sender.hasPermission("s-playerwarps.command.playerwarp.teleport") }
                .then(Commands.argument("identifier", StringArgumentType.string())
                    .suggests { ctx, builder ->
                        val input = try {
                            StringArgumentType.getString(ctx, "identifier").lowercase()
                        } catch (e: IllegalArgumentException) { "" }

                        CompletableFuture.supplyAsync {
                            PWManager.getAllWarps()
                                .map { it.identifier }
                                .filter { it.lowercase().startsWith(input) || it.lowercase() == input || input.isEmpty() }
                        }.thenApply { filteredWarps ->
                            filteredWarps.forEach { builder.suggest(it) }
                            builder.build()
                        }
                    }
                    .executes { ctx ->
                        val sender = ctx.source.sender as Player

                        val identifier = StringArgumentType.getString(ctx, "identifier")

                        val identifierRegex = Regex(config.getString("playerwarps.identifier.regex", "^[a-z_]+$"))
                        val minLen = config.getInt("playerwarps.identifier.minLength", 4)
                        val maxLen = config.getInt("playerwarps.identifier.maxLength", 16)

                        if (!identifierRegex.matches(identifier) || identifier.length !in minLen..maxLen || !PWManager.existsPlayerWarp(identifier)) {
                            sender.sendMessage(mm.deserialize(prefix + messages.getString("commands.playerwarp.teleport.notFound")
                                .replace("%warp%", identifier)))
                            return@executes 1
                        }

                        val playerWarp = PWManager.getPlayerWarp(identifier) ?: run {
                            sender.sendMessage(mm.deserialize(prefix + messages.getString("commands.playerwarp.teleport.notFound")
                                .replace("%warp%", identifier)))
                            return@executes 1
                        }

                        pendingTeleports[sender.uniqueId]?.let { (existingTask, _) ->
                            existingTask.cancel()
                            pendingTeleports.remove(sender.uniqueId)
                        }

                        val delayEnabled = config.getBoolean("playerwarps.teleportation.delay.enabled", true)
                        val delaySeconds = config.getInt("playerwarps.teleportation.delay.delay", 3)

                        if (!delayEnabled || delaySeconds <= 0) {
                            sender.sendMessage(mm.deserialize(prefix + messages.getString("commands.playerwarp.teleport.teleporting")
                                .replace("%warp%", identifier)))
                            playerWarp.teleport(sender)
                            sender.sendMessage(mm.deserialize(prefix + messages.getString("commands.playerwarp.teleport.success")
                                .replace("%warp%", identifier)))

                            if (config.getBoolean("sounds.teleportSound.enabled", true)) {
                                val soundName = config.getString("sounds.teleportSound.sound", "ENTITY_ENDERMAN_TELEPORT")!!
                                val volume = config.getDouble("sounds.teleportSound.volume", 1.0).toFloat()
                                val pitch = config.getDouble("sounds.teleportSound.pitch", 1.0).toFloat()
                                try {
                                    sender.playSound(sender.location, normalizeSoundKey(soundName), volume, pitch)
                                } catch (e: Exception) {
                                    main.logger.warning("Error while playing sound $soundName: ${e.message}")
                                }
                            }
                        } else {
                            sender.sendMessage(mm.deserialize(prefix + messages.getString("commands.playerwarp.teleport.teleporting")
                                .replace("%warp%", identifier)))
                            var remaining = delaySeconds

                            val task = main.server.scheduler.runTaskTimer(main, Runnable {
                                if (!sender.isOnline) {
                                    pendingTeleports.remove(sender.uniqueId)
                                    return@Runnable
                                }

                                remaining -= 1
                                if (remaining <= 0) {
                                    playerWarp.teleport(sender)
                                    sender.sendMessage(mm.deserialize(prefix + messages.getString("commands.playerwarp.teleport.success")
                                        .replace("%warp%", identifier)))

                                    if (config.getBoolean("sounds.teleportSound.enabled", true)) {
                                        val soundName = config.getString("sounds.teleportSound.sound", "ENTITY_ENDERMAN_TELEPORT")!!
                                        val volume = config.getDouble("sounds.teleportSound.volume", 1.0).toFloat()
                                        val pitch = config.getDouble("sounds.teleportSound.pitch", 1.0).toFloat()
                                        try {
                                            sender.playSound(sender.location, normalizeSoundKey(soundName), volume, pitch)
                                        } catch (e: Exception) {
                                            main.logger.warning("Error while playing sound $soundName: ${e.message}" )
                                        }
                                    }

                                    pendingTeleports[sender.uniqueId]?.first?.cancel()
                                    pendingTeleports.remove(sender.uniqueId)
                                } else {
                                    if (config.getBoolean("sounds.delaySound.enabled", true)) {
                                        val delaySoundName = config.getString("sounds.delaySound.sound", "BLOCK_NOTE_BLOCK_BASS")!!
                                        val delaySoundVolume = config.getDouble("sounds.delaySound.volume", 1.0).toFloat()
                                        val delaySoundPitch = config.getDouble("sounds.delaySound.pitch", 1.0).toFloat()
                                        try {
                                            sender.playSound(sender.location, normalizeSoundKey(delaySoundName), delaySoundVolume, delaySoundPitch)
                                        } catch (e: Exception) {
                                            main.logger.warning("Error while playing sound $delaySoundName: ${e.message}")
                                        }
                                    }
                                }

                            }, 0L, 20L)

                            pendingTeleports[sender.uniqueId] = Pair(task, sender.location)
                        }

                        return@executes 1
                    }
                )
            )
            .then(Commands.literal("menu")
                .requires { ctx -> ctx.sender.hasPermission("s-playerwarps.command.playerwarp.menu") }
                .executes { ctx ->
                    val sender = ctx.source.sender as Player

                    GuiManager.openMainMenu(sender)

                    return@executes 1
                }
            )
    }

    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        val player = event.player
        val from = event.from
        val to = event.to

        val stored = pendingTeleports[player.uniqueId] ?: return
        if (!config.getBoolean("playerwarps.teleportation.delay.cancelOnMove", true)) return
        if (from.distanceSquared(to) < 0.02) return

        val (task, _) = stored
        if (!task.isCancelled) task.cancel()
        pendingTeleports.remove(player.uniqueId)

        if (config.getBoolean("sounds.cancelSound.enabled", true)) {
            val cancelSoundName = config.getString("sounds.cancelSound.sound", "ENTITY_VILLAGER_NO")!!
            val cancelVolume = config.getDouble("sounds.cancelSound.volume", 1.0).toFloat()
            val cancelPitch = config.getDouble("sounds.cancelSound.pitch", 1.0).toFloat()
            try {
                player.playSound(player.location, normalizeSoundKey(cancelSoundName), cancelVolume, cancelPitch)
            } catch (_: Exception) { }
        }

        player.sendMessage(mm.deserialize(prefix + (messages.getString("commands.playerwarp.teleport.canceled"))))
    }

}

