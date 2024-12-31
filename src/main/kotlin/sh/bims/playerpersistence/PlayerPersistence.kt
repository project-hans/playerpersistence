package sh.bims.playerpersistence

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.fabricmc.api.ModInitializer
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.Vec3d
import net.minecraft.world.GameMode
import net.minecraft.world.TeleportTarget
import java.sql.PreparedStatement
import java.util.*

class PlayerPersistence : ModInitializer {

    override fun onInitialize() {
        val node = Database.SERVER_NODE
        val query = """
            CREATE TABLE IF NOT EXISTS player_inventories (
                uuid UUID PRIMARY KEY,
                inventory_data JSONB NOT NULL,
                last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );

            CREATE TABLE IF NOT EXISTS player_enderchests (
                uuid UUID PRIMARY KEY,
                chest_data JSONB NOT NULL,
                last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );

            DO $$ BEGIN
                IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'vector3') THEN
                    CREATE TYPE vector3 AS (
                        x FLOAT8,
                        y FLOAT8,
                        z FLOAT8
                    );
                END IF;
            END $$;

            CREATE TABLE IF NOT EXISTS player_locations_$node (
                uuid UUID PRIMARY KEY,
                dimension TEXT NOT NULL,
                coordinates vector3 NOT NULL,
                gamemode TEXT NOT NULL,
                last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );""".trimIndent()

        Database.connection.prepareStatement(query).use { statement: PreparedStatement ->
            statement.executeUpdate()
        }
    }

    fun syncInventoryData(player: ServerPlayerEntity) {
        val invQuery = "SELECT inventory_data FROM player_inventories WHERE uuid = ?"
        Database.connection.prepareStatement(invQuery).use { statement: PreparedStatement ->
            statement.setObject(1, player.uuid)
            val resultSet = statement.executeQuery()
            if (resultSet.next()) {
                val inventoryData = resultSet.getString("inventory_data")
                deserializeInventory(player, inventoryData)
            }
        }
    }

    fun syncEnderChestData(player: ServerPlayerEntity) {
        val chestQuery = "SELECT chest_data FROM player_enderchests WHERE uuid = ?"
        Database.connection.prepareStatement(chestQuery).use { statement: PreparedStatement ->
            statement.setObject(1, player.uuid)
            val resultSet = statement.executeQuery()
            if (resultSet.next()) {
                val enderChestData = resultSet.getString("chest_data")
                deserializeEnderChest(player, enderChestData)
            }
        }
    }

    fun writeInventory(uuid: UUID, inventoryData: String) {
        val invQuery = """
        INSERT INTO player_inventories (uuid, inventory_data, last_updated) 
        VALUES (?, ?::jsonb, NOW()) 
        ON CONFLICT (uuid) 
        DO UPDATE SET inventory_data = EXCLUDED.inventory_data, last_updated = NOW();
        """
        Database.connection.prepareStatement(invQuery).use { statement: PreparedStatement ->
            statement.setObject(1, uuid)
            statement.setString(2, inventoryData)
            statement.executeUpdate()
        }
    }

    fun writeEnderChest(uuid: UUID, playerEnderChestData: String) {
        val enderChestQuery = """
        INSERT INTO player_enderchests (uuid, chest_data, last_updated) 
        VALUES (?, ?::jsonb, NOW()) 
        ON CONFLICT (uuid) 
        DO UPDATE SET chest_data = EXCLUDED.chest_data, last_updated = NOW();
        """

        Database.connection.prepareStatement(enderChestQuery).use { statement: PreparedStatement ->
            statement.setObject(1, uuid)
            statement.setString(2, playerEnderChestData)
            statement.executeUpdate()
        }
    }

    fun serializeInventory(player: ServerPlayerEntity): String {
        val invArray = JsonArray()

        // Serialize main inventory
        player.inventory.main.forEachIndexed { index, itemStack ->
            serializeStack(itemStack, index)?.let { invArray.add(it) }
        }

        // Serialize armor slots
        player.inventory.armor.forEachIndexed { index, itemStack ->
            serializeStack(itemStack, index + 100)?.let { invArray.add(it) }
        }

        // Serialize off-hand slot
        player.inventory.offHand.forEachIndexed { index, itemStack ->
            serializeStack(itemStack, index + 150)?.let { invArray.add(it) }
        }
        return invArray.toString()
    }

    fun serializeEnderChest(player: ServerPlayerEntity): String {
        val enderChestArray = JsonArray()
        player.enderChestInventory.heldStacks.forEachIndexed { index, itemStack ->
            serializeStack(itemStack, index)?.let { enderChestArray.add(it) }
        }
        return enderChestArray.toString()
    }

    fun serializeStack(itemStack: ItemStack, index: Int): JsonObject? {
        val jsonOps = JsonOps.INSTANCE
        if (!itemStack.isEmpty) {
            val itemJson = ItemStack.CODEC.encodeStart(jsonOps, itemStack).result().orElse(null)
            if (itemJson != null) {
                val jsonObject = JsonObject()
                jsonObject.addProperty("Slot", index)
                jsonObject.add("ItemStack", itemJson)
                return jsonObject
            }
        }
        return null
    }

    fun deserializeInventory(player: ServerPlayerEntity, inventoryData: String) {
        val jsonOps = JsonOps.INSTANCE
        val jsonArray = JsonParser.parseString(inventoryData).asJsonArray

        player.inventory.clear()

        jsonArray.forEach { jsonElement ->
            val jsonObject = jsonElement.asJsonObject
            val slot = jsonObject.get("Slot").asInt
            val itemJson = jsonObject.get("ItemStack")

            val itemStack = ItemStack.CODEC.parse(jsonOps, itemJson).result().orElse(ItemStack.EMPTY)

            when {
                slot < 36 -> player.inventory.setStack(slot, itemStack)  // Main inventory
                slot in 100..149 -> player.inventory.armor[slot - 100] = itemStack  // Armor slots
                slot in 150..199 -> player.inventory.offHand[slot - 150] = itemStack  // Off-hand slot
            }
        }
    }

    fun deserializeEnderChest(player: ServerPlayerEntity, inventoryData: String) {
        val jsonOps = JsonOps.INSTANCE
        val jsonArray = JsonParser.parseString(inventoryData).asJsonArray

        player.enderChestInventory.clear()

        jsonArray.forEach { jsonElement ->
            val jsonObject = jsonElement.asJsonObject
            val slot = jsonObject.get("Slot").asInt
            val itemJson = jsonObject.get("ItemStack")

            val itemStack = ItemStack.CODEC.parse(jsonOps, itemJson).result().orElse(ItemStack.EMPTY)

            player.enderChestInventory.setStack(slot, itemStack)
        }
    }

    fun writePlayerCoordinates(player: ServerPlayerEntity) {
        val serverNode = Database.SERVER_NODE
        val query = """
        INSERT INTO player_locations_$serverNode (uuid, dimension, coordinates, gamemode)
        VALUES (?, ?, ROW(?, ?, ?))
        ON CONFLICT (uuid)
        DO UPDATE SET
            dimension = EXCLUDED.dimension,
            coordinates = EXCLUDED.coordinates,
            gamemode = EXCLUDED.gamemode,
            last_updated = CURRENT_TIMESTAMP;
         """.trimIndent()

        Database.connection.prepareStatement(query).use { statement: PreparedStatement ->
            statement.setObject(1, player.uuid)
            statement.setString(2, player.world.registryKey.value.toString())
            statement.setDouble(3, player.pos.x)
            statement.setDouble(4, player.pos.y)
            statement.setDouble(5, player.pos.z)
            statement.setString(6, player.interactionManager.gameMode.toString())
            statement.executeUpdate()
        }
    }

    fun syncPlayerCoordinates(player: ServerPlayerEntity) {
        val serverNode = Database.SERVER_NODE
        val query = """
            SELECT
                dimension,
                (coordinates).x,
                (coordinates).y,
                (coordinates).z,
                gamemode
            FROM player_locations_$serverNode
            WHERE uuid = ?
            """.trimIndent()

        Database.connection.prepareStatement(query).use { statement ->
            statement.setObject(1, player.uuid)
            val resultSet = statement.executeQuery()
            if (resultSet.next()) {
                val dimension = resultSet.getString("dimension")
                val x = resultSet.getDouble("x")
                val y = resultSet.getDouble("y")
                val z = resultSet.getDouble("z")
                val gameMode = resultSet.getString("gamemode")
                player.changeGameMode(GameMode.valueOf(gameMode))
                player.server.worlds.forEach { world ->
                    if (world.registryKey.value.toString() == dimension) {
                        player.teleportTo(
                            TeleportTarget(
                                world,
                                Vec3d(x, y, z),
                                player.velocity,
                                player.yaw,
                                player.pitch
                            ) { })
                        return
                    }
                }
            }
        }
    }
}
