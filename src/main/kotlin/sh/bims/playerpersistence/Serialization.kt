package sh.bims.playerpersistence

import com.google.gson.*
import com.mojang.serialization.JsonOps
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.registry.RegistryOps
import eu.pb4.polymer.core.api.item.PolymerItemUtils
import net.minecraft.component.ComponentMap
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryWrapper.WrapperLookup
import net.minecraft.util.Identifier

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

    /* ────────────────────────── SERIALISE ONE SLOT ────────────────────────── */
    fun serializeStack(
        raw: ItemStack,
        slot: Int,
        lookup: WrapperLookup          // = player.server.registryManager
    ): JsonObject? {
        if (raw.isEmpty) return null

        // 1. get the true server stack (no disguise)
        val real = PolymerItemUtils.getRealItemStack(raw, lookup)

        // 2. write our own JSON
        val obj = JsonObject()
        obj.addProperty("slot", slot)
        obj.addProperty("id",   Registries.ITEM.getId(real.item).toString())
        obj.addProperty("count", real.count)

        if (!real.components.isEmpty) {
            val ops = RegistryOps.of(JsonOps.INSTANCE, lookup)
            val compsJson = ComponentMap.CODEC.encodeStart(ops, real.components)
                .result()
                .orElse(null)
            compsJson?.let { obj.add("components", it) }
        }
        return obj
    }

    /* ──────────────────────── DESERIALISE WHOLE ARRAY ─────────────────────── */
    fun deserializeStacks(
        inv: Inventory,
        json: String,
        lookup: WrapperLookup
    ) {
        val arr  = JsonParser.parseString(json).asJsonArray
        val ops  = RegistryOps.of(JsonOps.INSTANCE, lookup)

        inv.clear()

        for (el in arr) {
            val o       = el.asJsonObject
            val slot    = o["slot"].asInt
            val idStr  = o["id"].asString
            val itemId  = Identifier.of(idStr)
            val item    = Registries.ITEM.get(itemId)
            val count   = o["count"].asInt.coerceAtMost(item.maxCount)

            val stack   = ItemStack(item, count)

            if (o.has("components")) {
                val comps = ComponentMap.CODEC
                    .parse(ops, o["components"])
                    .result()
                    .orElse(ComponentMap.EMPTY)
                stack.applyComponentsFrom(comps)   // 1.21 method
            }
            inv.setStack(slot, stack)
        }
    }


}