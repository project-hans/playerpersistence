package sh.bims.playerpersistence

import com.google.gson.*
import com.mojang.serialization.JsonOps
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.component.ComponentMap

import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import eu.pb4.polymer.core.api.item.PolymerItemUtils
import net.minecraft.registry.RegistryWrapper

object Serialization {

    fun serializeInventory(player: ServerPlayerEntity): String {
        val invArray = JsonArray()
        // Serialize armor slots

        val lookup = player.server.registryManager
        for (i in 0..<player.inventory.size()) {
            serializeStack(player.inventory.getStack(i), i, lookup)?.let { invArray.add(it) }
        }
        return invArray.toString()
    }

    fun serializeEnderChest(player: ServerPlayerEntity): String {
        val enderChestArray = JsonArray()
        val lookup = player.server.registryManager
        player.enderChestInventory.heldStacks.forEachIndexed { index, itemStack ->
            serializeStack(itemStack, index, lookup)?.let { enderChestArray.add(it) }
        }
        return enderChestArray.toString()
    }

    fun deserializeInventory(player: ServerPlayerEntity, inventoryData: String) {
        deserializeStacks(player.inventory, inventoryData)
    }

    fun deserializeEnderChest(player: ServerPlayerEntity, enderChestData: String) {
        deserializeStacks(player.enderChestInventory, enderChestData)
    }




    // ----------  SERIALISE  ----------
    fun serializeStack(stack: ItemStack, slot: Int, lookup: RegistryWrapper.WrapperLookup): JsonObject? {
        if (stack.isEmpty) return null                      // don't spam the DB with empties

        // Peel off the vanilla disguise if this is a Polymer item
        val serverStack = PolymerItemUtils.getRealItemStack(stack, lookup)

        val obj = JsonObject()
        obj.addProperty("slot",  slot)
        obj.addProperty("id",    Registries.ITEM.getId(serverStack.item).toString())
        obj.addProperty("count", serverStack.count)

        // Modern item data lives in Data Components, not old-school NBT
        if (!serverStack.components.isEmpty) {
            ComponentMap.CODEC
                .encodeStart(JsonOps.INSTANCE, serverStack.components)
                .result()
                .ifPresent { compsJson -> obj.add("components", compsJson) }
        }
        return obj
    }

    // ----------  DESERIALISE  ----------
    private fun deserializeStacks(
        inventory: Inventory,
        data: String
    ) {
        JsonParser.parseString(data).asJsonArray.forEach { element ->
            val o     = element.asJsonObject
            val slot  = o["slot"].asInt
            val id    = Identifier.tryParse(o["id"].asString)
            val cnt   = o["count"].asInt
            val stack = ItemStack(Registries.ITEM[id], cnt)

            // restore components (if present)
            if (o.has("components")) {
                ComponentMap.CODEC
                    .parse(JsonOps.INSTANCE, o["components"])
                    .result()
                    .ifPresent { stack.applyComponentsFrom(it) }
            }
            inventory.setStack(slot, stack)
        }
    }






}