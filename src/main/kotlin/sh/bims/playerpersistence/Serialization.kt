package sh.bims.playerpersistence

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import com.google.gson.JsonElement
import com.mojang.serialization.DynamicOps
import net.minecraft.registry.DynamicRegistryManager    // = RegistryAccess
import net.minecraft.registry.RegistryOps
import org.jetbrains.exposed.sql.exposedLogger

object Serialization {

    fun serializeInventory(player: ServerPlayerEntity): String {
        val invArray = JsonArray()
        // Serialize armor slots
        for (i in 0..<player.inventory.size()) {
            serializeStack(player.inventory.getStack(i), i, player.server.registryManager)?.let { invArray.add(it) }
        }
        return invArray.toString()
    }

    fun serializeEnderChest(player: ServerPlayerEntity): String {
        val enderChestArray = JsonArray()
        player.enderChestInventory.heldStacks.forEachIndexed { index, itemStack ->
            serializeStack(itemStack, index, player.server.registryManager)?.let { enderChestArray.add(it) }
        }
        return enderChestArray.toString()
    }

    private fun serializeStack(itemStack: ItemStack, index: Int, registries: DynamicRegistryManager): JsonObject? {
        if (itemStack.isEmpty) return null                       // nothing to do

        val jsonOps: DynamicOps<JsonElement> =
            RegistryOps.of(JsonOps.INSTANCE, registries)

        val stackJson = ItemStack.CODEC.encodeStart(jsonOps, itemStack)
            .resultOrPartial { msg ->
                exposedLogger.warn("Could not serialise stack in slot {}: {}", index, msg)
            }
            .orElse(null)

        return stackJson?.let { json ->
            JsonObject().apply {
                addProperty("Slot", index)
                add("ItemStack", json)
            }
        }
    }

    fun deserializeInventory(player: ServerPlayerEntity, inventoryData: String) {
        deserializeStacks(player.inventory, inventoryData, player.server.registryManager)
    }

    fun deserializeEnderChest(player: ServerPlayerEntity, enderChestData: String) {
        deserializeStacks(player.enderChestInventory, enderChestData, player.server.registryManager)
    }

    fun deserializeStacks(inventory: Inventory, stackData: String, registries: DynamicRegistryManager) {
        val jsonOps: DynamicOps<JsonElement> =
            RegistryOps.of(JsonOps.INSTANCE, registries)

        val jsonArray = JsonParser.parseString(stackData).asJsonArray
        inventory.clear()

        jsonArray.forEach { element ->
            val obj = element.asJsonObject
            val slot = obj["Slot"].asInt
            val itemJson = obj["ItemStack"]

            val stack = ItemStack.CODEC.parse(jsonOps, itemJson)
                .resultOrPartial { msg ->
                    exposedLogger.warn("Could not read stack in slot {}: {}", slot, msg)
                }
                .orElse(ItemStack.EMPTY)

            if (slot in 0 until inventory.size()) {
                inventory.setStack(slot, stack)
            } else {
                exposedLogger.warn("Saved slot {} outside inventory bounds (size {})",
                    slot, inventory.size())
            }
        }
    }
}