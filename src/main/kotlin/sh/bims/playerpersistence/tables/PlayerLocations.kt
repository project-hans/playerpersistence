package sh.bims.playerpersistence.tables

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.UUID

object PlayerLocations : Table("player_locations") {
    val uuid: Column<UUID> = uuid("uuid")
    val node: Column<String> = varchar("node", 64)
    val dimension: Column<String> = varchar("dimension", 64)
    val x: Column<Double> = double("x")
    val y: Column<Double> = double("y")
    val z: Column<Double> = double("z")
    val gamemode: Column<String> = varchar("gamemode", 64)
    val lastUpdated: Column<java.time.Instant> = timestamp("last_updated").defaultExpression(org.jetbrains.exposed.sql.javatime.CurrentTimestamp)

    override val primaryKey = PrimaryKey(uuid, node, name = "PK_PlayerLocations_UUID_Node")
}