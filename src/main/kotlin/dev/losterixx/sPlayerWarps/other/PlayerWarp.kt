package dev.losterixx.sPlayerWarps.other

import org.bukkit.Location
import java.util.UUID

data class PlayerWarp(
    val identifier: String,
    val owner: UUID,
    val displayName: String?,
    val location: Location
)
