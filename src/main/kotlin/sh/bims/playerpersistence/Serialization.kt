package sh.bims.playerpersistence

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity

object Serialization {
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
        val jsonOps = JsonOps.INSTANCE
        val jsonArray = JsonParser.parseString(inventoryData).asJsonArray

        player.inventory.clear()

        jsonArray.forEach { jsonElement ->
            val jsonObject = jsonElement.asJsonObject
            val slot = jsonObject.get("Slot").asInt
            val itemJson = jsonObject.get("ItemStack")

            val itemStack = ItemStack.CODEC.parse(jsonOps, itemJson).result().orElse(ItemStack.EMPTY)

            when {
                slot < 36 -> player.inventory.setStack(slot, itemStack) // Main inventory
                slot in 100..149 -> player.inventory.armor[slot - 100] = itemStack // Armor slots
                slot in 150..199 -> player.inventory.offHand[slot - 150] = itemStack // Off-hand slot
            }
        }
    }

    fun deserializeEnderChest(player: ServerPlayerEntity, enderChestData: String) {
        val jsonOps = JsonOps.INSTANCE
        val jsonArray = JsonParser.parseString(enderChestData).asJsonArray

        player.enderChestInventory.clear()

        jsonArray.forEach { jsonElement ->
            val jsonObject = jsonElement.asJsonObject
            val slot = jsonObject.get("Slot").asInt
            val itemJson = jsonObject.get("ItemStack")

            val itemStack = ItemStack.CODEC.parse(jsonOps, itemJson).result().orElse(ItemStack.EMPTY)

            player.enderChestInventory.setStack(slot, itemStack)
        }
    }
}