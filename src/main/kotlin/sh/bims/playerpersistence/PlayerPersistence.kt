@file:Suppress("unused")

package sh.bims.playerpersistence

import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.metadata.ModMetadata
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.Vec3d
import net.minecraft.world.GameMode
import net.minecraft.world.TeleportTarget
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import sh.bims.playerpersistence.tables.PlayerEnderchests
import sh.bims.playerpersistence.tables.PlayerInventories
import sh.bims.playerpersistence.tables.PlayerLocations
import java.time.Instant
import java.util.*

class PlayerPersistence : ModInitializer {
    companion object {
        private var modMetadata: ModMetadata = FabricLoader.getInstance().getModContainer("playerpersistence").get().metadata
        var logger: Logger = LoggerFactory.getLogger(modMetadata.id)
    }
    
    private var url: String = ""
    private var user: String = ""
    private var pass: String = ""
    private var serverNode: String = ""

    fun initialize(url: String, user: String, pass: String, serverNode: String) {
        this.url = url
        this.user = user
        this.pass = pass
        this.serverNode = serverNode

        if (url.isEmpty() || user.isEmpty() || pass.isEmpty() || serverNode.isEmpty()) {
            logger.error("Initialization failed: One or more parameters (url, user, pass, serverNode) are empty.")
            throw IllegalArgumentException("All parameters (url, user, pass, serverNode) must be non-empty.")
        }

        try {
            DatabaseManager.initialize(url, user, pass, serverNode)
            logger.info("DatabaseManager initialized successfully for server node: $serverNode")

            transaction {
                addLogger(StdOutSqlLogger)
                SchemaUtils.createMissingTablesAndColumns(PlayerEnderchests, PlayerInventories, PlayerLocations)
            }
            logger.info("Database schemas created/updated successfully for server node: $serverNode.")
        } catch (error: Exception) {
            logger.error("Error during initialization: ${error.message}", error)
            throw error
        }
    }
    
    override fun onInitialize() {
        logger.info("Loaded library.")
    }
    
    fun syncInventoryData(player: ServerPlayerEntity) {
        transaction {
            val inventoryData = PlayerInventories
                .select(PlayerInventories.inventoryData)
                .where { PlayerInventories.uuid eq player.uuid }
                .singleOrNull()?.get(PlayerInventories.inventoryData)

            inventoryData?.let { Serialization.deserializeInventory(player, it) }
        }
    }

    fun syncEnderChestData(player: ServerPlayerEntity) {
        transaction {
            val chestData = PlayerEnderchests
                .select(PlayerEnderchests.chestData)
                .where { PlayerEnderchests.uuid eq player.uuid }
                .singleOrNull()?.get(PlayerEnderchests.chestData)

            chestData?.let { Serialization.deserializeEnderChest(player, it) }
        }
    }

    fun writeInventory(uuid: UUID, inventoryData: String) {
        transaction {
            PlayerInventories.upsert(
                PlayerInventories.uuid
            ) {
                it[this.uuid] = uuid
                it[this.inventoryData] = inventoryData
                it[this.lastUpdated] = Instant.now()
            }
        }
    }

    fun writeEnderChest(uuid: UUID, playerEnderChestData: String) {
        transaction {
            PlayerEnderchests.upsert(
                PlayerEnderchests.uuid
            ) {
                it[this.uuid] = uuid
                it[this.chestData] = playerEnderChestData
                it[this.lastUpdated] = Instant.now()
            }
        }
    }

    fun writePlayerCoordinates(player: ServerPlayerEntity) {
        transaction {
            PlayerLocations.upsert(
                PlayerLocations.uuid, PlayerLocations.node
            ) {
                it[uuid] = player.uuid
                it[node] = serverNode
                it[dimension] = player.world.registryKey.value.toString()
                it[gamemode] = player.interactionManager.gameMode.toString()
                it[x] = player.pos.x
                it[y] = player.pos.y
                it[z] = player.pos.z
                it[lastUpdated] = Instant.now()
            }
        }
    }

    fun syncPlayerCoordinates(player: ServerPlayerEntity) {
        transaction {
            val location = PlayerLocations.selectAll()
                .where { PlayerLocations.uuid eq player.uuid and (PlayerLocations.node eq serverNode) }
                .singleOrNull()

            location?.let {
                val dimension = it[PlayerLocations.dimension]
                val gamemode = it[PlayerLocations.gamemode]
                val x = it[PlayerLocations.x]
                val y = it[PlayerLocations.y]
                val z = it[PlayerLocations.z]

                player.changeGameMode(GameMode.valueOf(gamemode))
                player.server.worlds.forEach { world ->
                    if (world.registryKey.value.toString() == dimension) {
                        player.teleportTo(
                            TeleportTarget(
                                world,
                                Vec3d(x, y, z),
                                player.velocity,
                                player.yaw,
                                player.pitch
                            ) { }
                        )
                    }
                }
            }
        }
    }
}
