package sh.bims.playerpersistence.tables

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.UUID

object PlayerInventories : Table("player_inventories") {
    val uuid: Column<UUID> = uuid("uuid")
    val inventoryData: Column<String> = text("inventory_data")
    val lastUpdated: Column<java.time.Instant> = timestamp("last_updated").defaultExpression(org.jetbrains.exposed.sql.javatime.CurrentTimestamp)

    override val primaryKey = PrimaryKey(uuid, name = "PK_PlayerInventories_UUID")
}