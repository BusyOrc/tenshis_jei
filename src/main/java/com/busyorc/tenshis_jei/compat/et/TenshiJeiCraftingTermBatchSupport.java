package com.busyorc.tenshis_jei.compat.et;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Craft-batch support added to AE2's CraftingTermSlot (and therefore to ET's ETCraftingSlot)
 * by our mixin. Unlike the JEI fork's jei$craftBatch, the result overflow is routed through
 * the generic MEStorageMenu accessor, so ET terminals spill excess results into their ME
 * network exactly like AE2 terminals do.
 */
public interface TenshiJeiCraftingTermBatchSupport {
    int tenshiJei$craftBatch(AbstractContainerMenu menu, Player player, ItemStack result, int times);
}
