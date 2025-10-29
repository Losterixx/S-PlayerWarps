package dev.losterixx.sPlayerWarps.other

import dev.losterixx.sPlayerWarps.Main
import dev.losterixx.sapi.utils.builder.ItemBuilder
import dev.losterixx.sapi.utils.config.ConfigManager
import dev.triumphteam.gui.builder.item.PaperItemBuilder
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object GuiManager {

    private val main = Main.instance
    private val mm = main.miniMessage
    private val config get() = ConfigManager.getConfig("config")
    private val prefix get() = config.getString("prefix") ?: Main.DEFAULT_PREFIX
    private val messages get() = ConfigManager.getConfig(config.getString("langFile", "english"))
    private val mainMenu get() = ConfigManager.getConfig("mainMenu")

    private val refreshTasks: MutableMap<UUID, BukkitTask> = ConcurrentHashMap()

    fun openMainMenu(player: Player) {
        val title = mm.deserialize(mainMenu.getString("title", "config error"))
        val rows = mainMenu.getInt("rows", 6).coerceIn(1, 6)
        val autoRefresh = mainMenu.getInt("autoRefresh", -1)

        val gui = Gui.paginated()
            .title(title)
            .rows(rows)
            //.pageSize(mainMenu.getSection("extra")?.getSection("warpButton")?.getIntList("slots", listOf()))
            .pageSize(mainMenu.getSection("extra")?.getSection("warpButton")?.getIntList("slots", listOf())!!.size)
            .create()

        val contentSection = mainMenu.getSection("content")
        if (contentSection != null) {
            for (key in contentSection.getRoutesAsStrings(false)) {
                val entry = contentSection.getSection(key) ?: continue
                val slots = entry.getIntList("slots")
                val itemSection = entry.getSection("item") ?: continue
                val guiItem = ItemBuilder.buildGuiItemFromConfig(itemSection) ?: continue
                for (slot in slots) {
                    gui.setItem(slot, guiItem)
                }
            }
        }

        val buttons = mainMenu.getSection("buttons")
        if (buttons != null) {
            val closeSection = buttons.getSection("closeButton")
            if (closeSection != null && closeSection.getBoolean("enabled", true)) {
                val slot = closeSection.getInt("slot", 49)
                val originalItem = ItemBuilder.buildGuiItemFromConfig(closeSection.getSection("item"))
                val itemStack = originalItem?.itemStack ?: PaperItemBuilder.from(Material.BARRIER).name(mm.deserialize("config error")).build()
                val closeItem = GuiItem(itemStack) { event ->
                    val player = event.whoClicked as? Player
                    player?.closeInventory()
                }
                gui.setItem(slot, closeItem)
            }

            val nextSection = buttons.getSection("nextPage")
            if (nextSection != null && nextSection.getBoolean("enabled", true)) {
                val slot = nextSection.getInt("slot", 52)
                val originalItem = ItemBuilder.buildGuiItemFromConfig(nextSection.getSection("item"))
                val itemStack = originalItem?.itemStack ?: PaperItemBuilder.from(Material.BARRIER).name(mm.deserialize("config error")).build()
                val nextItem = GuiItem(itemStack) { event ->
                    gui.next()
                }
                gui.setItem(slot, nextItem)
            }

            val prevSection = buttons.getSection("previousPage")
            if (prevSection != null && prevSection.getBoolean("enabled", true)) {
                val slot = prevSection.getInt("slot", 46)
                val originalItem = ItemBuilder.buildGuiItemFromConfig(prevSection.getSection("item"))
                val itemStack = originalItem?.itemStack ?: PaperItemBuilder.from(Material.BARRIER).name(mm.deserialize("config error")).build()
                val prevItem = GuiItem(itemStack) { event ->
                    gui.previous()
                }
                gui.setItem(slot, prevItem)
            }

        }

        val warpExtra = mainMenu.getSection("extra")?.getSection("warpButton")
        val warpNameTemplate = warpExtra?.getSection("item")?.getString("name", "%warp_displayname%")!!
        val warpLoreTemplate = warpExtra?.getSection("item")?.getStringList("lore", listOf())!!
        val warps = PWManager.getAllWarps().sortedByDescending { w -> w.uniqueUses.size }

        for (warp in warps) {
            val material = warp.material
            val displayName = warp.displayName ?: warp.identifier
            val owner = main.server.getOfflinePlayer(warp.owner).name ?: "???"
            val uniqueUses = warp.uniqueUses.size
            val uses = warp.totalUses

            val nameStr = warpNameTemplate
                .replace("%warp_displayname%", displayName)
                .replace("%warp_owner%", owner)
                .replace("%warp_uses%", uses.toString())
                .replace("%warp_unique_uses%", uniqueUses.toString())

            val loreList = warpLoreTemplate.map { line ->
                line.replace("%warp_displayname%", displayName)
                    .replace("%warp_owner%", owner)
                    .replace("%warp_uses%", uses.toString())
                    .replace("%warp_unique_uses%", uniqueUses.toString())
            }

            val itemStack = ItemStack(material)
            val meta = itemStack.itemMeta
            meta?.displayName(mm.deserialize(nameStr).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE))
            meta?.lore(loreList.map { mm.deserialize(it).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE) })
            itemStack.itemMeta = meta

            val guiItem = GuiItem(itemStack) { event ->
                val player = event.whoClicked as? Player ?: return@GuiItem
                player.performCommand("playerwarp teleport ${warp.identifier}")
                player.closeInventory()
            }

            gui.addItem(guiItem)
        }

        gui.disableAllInteractions()
        gui.setCloseGuiAction { event ->
            val p = event.player as? Player
            refreshTasks.remove(p?.uniqueId)?.cancel()
        }
        gui.open(player)

        if (autoRefresh > 0) {
            refreshTasks.remove(player.uniqueId)?.cancel()

            val task = main.server.scheduler.runTaskTimer(main, Runnable {
                if (!player.isOnline) {
                    refreshTasks.remove(player.uniqueId)?.cancel()
                    return@Runnable
                }

                gui.update()
            }, autoRefresh * 20L, autoRefresh * 20L)

            refreshTasks[player.uniqueId] = task
        }
    }
}