package com.busyorc.tenshis_jei.compat.wcwt;

import com.busyorc.tenshis_jei.compat.et.EtLog;
import mezz.jei.common.bookmarks.ServerBookmarkCraftingGridFill;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.items.storage.ViewCellItem;
import appeng.menu.me.items.CraftingTermMenu;
import appeng.util.prioritylist.IPartitionList;

/**
 * 从 WCWT 终端已连接的网络存储里抽取配方材料（精确匹配）。
 * 与 ET 的 EtNetworkMaterialSource 同思路，但按 WCWT 的 CraftingTermMenu 泛型实现；
 * 锻造台/工作台填料的用量都不大，这里只做精确匹配即可。
 */
class WcwtNetworkMaterialSource implements ServerBookmarkCraftingGridFill.ExternalIngredientSource {
    private final CraftingTermMenu menu;
    private final MEStorage storage;
    private final IEnergySource energySource;
    private final IActionSource actionSource;
    private final IPartitionList viewCellFilter;

    WcwtNetworkMaterialSource(CraftingTermMenu menu, ServerPlayer player) {
        this.menu = menu;
        this.storage = menu.getHost().getInventory();
        this.energySource = menu.getEnergySource();
        this.actionSource = menu.getActionSource();
        this.viewCellFilter = ViewCellItem.createItemFilter(menu.getViewCells());
    }

    @Override
    public ItemStack extract(int slotIndex, ItemStack template, int amount) {
        if (!menu.getLinkStatus().connected() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        AEItemKey key = AEItemKey.of(template);
        if (key == null || !isListed(key)) {
            return ItemStack.EMPTY;
        }
        long extracted = StorageHelper.poweredExtraction(energySource, storage, key, amount, actionSource);
        if (extracted <= 0) {
            EtLog.info("[ET-jei] wcwt network miss: {} x{}", key, amount);
            return ItemStack.EMPTY;
        }
        return key.toStack((int) extracted);
    }

    @Override
    public long countAvailable(int slotIndex, ItemStack template) {
        if (!menu.getLinkStatus().connected()) {
            return 0;
        }
        AEItemKey key = AEItemKey.of(template);
        if (key == null || !isListed(key)) {
            return 0;
        }
        return storage.getAvailableStacks().get(key);
    }

    private boolean isListed(AEItemKey key) {
        return viewCellFilter == null || viewCellFilter.isEmpty() || viewCellFilter.isListed(key);
    }
}
