package sh.bims.playerpersistence

import com.google.gson.*
import com.mojang.serialization.JsonOps
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.registry.RegistryOps
import eu.pb4.polymer.core.api.item.PolymerItemUtils
import net.minecraft.registry.RegistryWrapper.WrapperLookup
import sh.bims.playerpersistence.PlayerPersistence.Companion.logger

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
        val lookup = player.server.registryManager
        deserializeStacks(player.inventory, inventoryData, lookup)
    }

    fun deserializeEnderChest(player: ServerPlayerEntity, enderChestData: String) {
        val lookup = player.server.registryManager
        deserializeStacks(player.enderChestInventory, enderChestData, lookup)
    }

    /* ─────────────────────────────── SERIALISE ONE SLOT ─────────────────────────────── */
    fun serializeStack(
        stack: ItemStack,
        slot: Int,
        lookup: WrapperLookup          // e.g. player.server.registryManager
    ): JsonObject? {
        if (stack.isEmpty) return null

        // Strip Polymer disguise → real server item
        val real = PolymerItemUtils.getRealItemStack(stack, lookup)

        // Registry-aware ops keep every component (enchants, name, …)
        val ops = RegistryOps.of(JsonOps.INSTANCE, lookup)

        val jsonStack = ItemStack.CODEC.encodeStart(ops, real)
            .resultOrPartial { logger.error("Stack encode failed: {}", it) }
            .orElse(null) ?: return null

        return JsonObject().apply {
            addProperty("slot", slot)
            add("stack", jsonStack)     // full codec payload
        }
    }

    /* ─────────────────────────── DESERIALISE WHOLE INVENTORY ────────────────────────── */
    fun deserializeStacks(
        inventory: Inventory,
        data: String,
        lookup: WrapperLookup
    ) {
        val ops = RegistryOps.of(JsonOps.INSTANCE, lookup)
        val array = JsonParser.parseString(data).asJsonArray

        inventory.clear()
        for (elem in array) {
            val obj   = elem.asJsonObject
            val slot  = obj["slot"].asInt
            val stack = ItemStack.CODEC.parse(ops, obj["stack"])
                .resultOrPartial { logger.error("Stack decode failed: {}", it) }
                .orElse(ItemStack.EMPTY)

            inventory.setStack(slot, stack)
        }
    }

}