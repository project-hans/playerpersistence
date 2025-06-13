package sh.bims.playerpersistence.tables

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.timestamp
import sh.bims.playerpersistence.PlayerPersistence

import java.util.UUID

object PlayerEnderchests : Table("player_enderchests_" + PlayerPersistence.serverNode) {
    val uuid: Column<UUID> = uuid("uuid")
    val chestData: Column<String> = text("chest_data")
    val lastUpdated: Column<java.time.Instant> = timestamp("last_updated").defaultExpression(org.jetbrains.exposed.sql.javatime.CurrentTimestamp)

    override val primaryKey = PrimaryKey(uuid, name = "PK_PlayerEnderchests_UUID")
}