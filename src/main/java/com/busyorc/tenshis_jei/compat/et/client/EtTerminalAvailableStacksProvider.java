package com.busyorc.tenshis_jei.compat.et.client;

import com.busyorc.tenshis_jei.TenshisJeiLog;
import me.myogoo.extendedterminal.menu.extendedterminal.ETTerminalMenu;
import mezz.jei.gui.bookmarks.chain.BookmarkExternalStorageSnapshots;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAvailableStacksProviders;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Adds the open ET terminal's network contents to the stacks available for bookmark
 * recipe-chain crafting. Mirrors the fork's Ae2AvailableStacksProvider (hard-wired to
 * AE2's CraftingTermMenu); snapshots are read fresh on every call (the fork's snapshot
 * layer already controls refresh cadence, so no extra caching here).
 */
public class EtTerminalAvailableStacksProvider implements BookmarkAvailableStacksProviders.Provider {
    @Override
    public Optional<List<ItemStack>> getAvailableStacks(AbstractContainerMenu menu) {
        if (!(menu instanceof ETTerminalMenu)) {
            return Optional.empty();
        }
        Optional<List<ItemStack>> stacks = BookmarkExternalStorageSnapshots.readEntries(menu)
                .map(BookmarkExternalStorageSnapshots::toAvailableStacks);
        stacks.ifPresent(list -> TenshisJeiLog.info("[ET-jei] available stacks for ET terminal ({} entries): {}", list.size(), list));
        return stacks;
    }
}
