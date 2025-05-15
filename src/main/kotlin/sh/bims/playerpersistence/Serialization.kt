package sh.bims.playerpersistence

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity

object Serialization {
    fun serializeInventory(player: ServerPlayerEntity): String {
        val invArray = JsonArray()
        // Serialize armor slots
        for (i in 0..<player.inventory.size()) {
            serializeStack(player.inventory.getStack(i), i)?.let { invArray.add(it) }
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

    private fun serializeStack(itemStack: ItemStack, index: Int): JsonObject? {
        val jsonOps = JsonOps.INSTANCE
        if (!itemStack.isEmpty) {
            val itemJson = ItemStack.CODEC.encodeStart(jsonOps, itemStack).result().orElse(null)
            if (itemJson != null) {
                return JsonObject().apply {
                    addProperty("Slot", index)
                    add("ItemStack", itemJson)
                }
            }
        }
        return null
    }

    fun deserializeInventory(player: ServerPlayerEntity, inventoryData: String) {
        deserializeStacks(player.inventory, inventoryData)
    }

    fun deserializeEnderChest(player: ServerPlayerEntity, enderChestData: String) {
        deserializeStacks(player.enderChestInventory, enderChestData)
    }

    fun deserializeStacks(inventory: Inventory, stackData: String) {
        val jsonOps = JsonOps.INSTANCE
        val jsonArray = JsonParser.parseString(stackData).asJsonArray
        inventory.clear()
        jsonArray.forEach { jsonElement ->
            val jsonObject = jsonElement.asJsonObject
            val slot = jsonObject["Slot"].asInt
            val itemJson = jsonObject["ItemStack"]

            val itemStack = ItemStack.CODEC.parse(jsonOps, itemJson).result().orElse(ItemStack.EMPTY)
            inventory.setStack(slot, itemStack)
        }
    }
}