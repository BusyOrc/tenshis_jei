package com.busyorc.tenshis_jei.compat.ae2;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.locator.MenuLocators;
import com.busyorc.tenshis_jei.compat.curios.CuriosCompatBridge;
import com.busyorc.tenshis_jei.compat.eaep.EaepCompatBridge;
import com.busyorc.tenshis_jei.compat.curios.CuriosItemLocator;
import mezz.jei.common.bookmarks.BookmarkPullTarget;
import mezz.jei.common.bookmarks.ServerBookmarkExternalStoragePull;
import mezz.jei.common.bookmarks.ServerBookmarkPullTransferHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.OptionalInt;

/**
 * V/shift+V 拉取配方树物品时，若玩家身上带着 AE2 无线终端（或附属的无线终端物品），
 * 且当前打开的菜单不是 MEStorageMenu（fork 的 Ae2BookmarkPullTransferHandler 已在
 * ME 终端菜单打开时接管），就通过无线终端直连其绑定的网络，无需打开 ME 终端即可
 * 从 ME 网络把物品直接拉进玩家背包。
 * <p>
 * 连接判定直接复用 AE2 的 {@code WirelessTerminalMenuHost#getLinkStatus()}（维度/接入点
 * 激活/范围/电量，含线缆内 AP 部件），与右键终端打开时的判定完全一致。
 */
public class WirelessBookmarkPullTransferHandler implements ServerBookmarkPullTransferHandler {

    @Override
    public OptionalInt pull(
        AbstractContainerMenu menu,
        int containerId,
        Container playerInventory,
        @Nullable ServerPlayer player,
        List<BookmarkPullTarget> targets
    ) {
        if (player == null || targets.isEmpty()) {
            return OptionalInt.empty();
        }
        if (!(player.level() instanceof ServerLevel)) {
            return OptionalInt.empty();
        }
        com.busyorc.tenshis_jei.TenshisJeiLog.info("[ET-jei] pull-srv: handler entered, targets=" + targets.size() + ", menu=" + menu.getClass().getName() + ", containerId=" + containerId);
        // EAEP 已加载 -> 用 EAEP 的定位+连接逻辑（AE2/WTLib/Curios/手持全覆盖，
        // 含 WTLib 量子桥与 EAE 终端宿主），逻辑/API 与 ExtendedAE_Plus 一致。
        if (EaepCompatBridge.isExtendedAEPlusLoaded()) {
            com.busyorc.tenshis_jei.TenshisJeiLog.info("[ET-jei] pull-srv: EAEP loaded, using WirelessTerminalLocator flow");
            java.util.OptionalInt eaepPull = EaepCompatBridge.pull(player, menu, containerId, playerInventory, targets);
            if (eaepPull.isPresent()) {
                return eaepPull;
            }
            // EAEP 判定无可用终端/未连接 -> 交给下方 AE2 降级逻辑（双保险，正常不会走到）
        }
        // 找终端：背包槽位优先，其次 Curios 饰品槽（可选前置）
        int slot = findWirelessTerminalSlot(playerInventory);
        ItemStack wireless;
        ItemMenuHostLocator locator;
        if (slot >= 0) {
            wireless = playerInventory.getItem(slot);
            locator = MenuLocators.forInventorySlot(slot);
        } else {
            com.busyorc.tenshis_jei.compat.curios.CuriosCompatBridge.CuriosTerminalRef ref =
                com.busyorc.tenshis_jei.compat.curios.CuriosCompatBridge.findWirelessTerminal(player);
            if (ref == null) {
                return OptionalInt.empty();
            }
            wireless = ref.stack();
            locator = new com.busyorc.tenshis_jei.compat.curios.CuriosItemLocator(ref.slotId(), ref.index());
        }
        if (wireless.isEmpty()) {
            return OptionalInt.empty();
        }
        WirelessTerminalItem item = (WirelessTerminalItem) wireless.getItem();
        WirelessTerminalMenuHost<?> wirelessHost = item.getMenuHost(player, locator, null);
        if (wirelessHost == null || !wirelessHost.getLinkStatus().connected()) {
            return OptionalInt.empty();
        }
        IGridNode node = wirelessHost.getActionableNode();
        if (node == null) {
            return OptionalInt.empty();
        }
        IGrid grid = node.getGrid();
        MEStorage storage = grid.getStorageService().getInventory();
        IEnergyService energy = grid.getEnergyService();
        IActionSource actionSource = IActionSource.ofPlayer(player);
        int moved = ServerBookmarkExternalStoragePull.pull(
            menu,
            containerId,
            playerInventory,
            targets,
            (target, amount) -> {
                AEItemKey key = AEItemKey.of(target.itemStack());
                if (key == null) {
                    return ItemStack.EMPTY;
                }
                long extracted = StorageHelper.poweredExtraction(energy, storage, key, amount, actionSource);
                if (extracted <= 0) {
                    return ItemStack.EMPTY;
                }
                ItemStack extractedStack = target.itemStack().copy();
                extractedStack.setCount((int) Math.min(extracted, Integer.MAX_VALUE));
                return extractedStack;
            }
        );
        if (moved > 0) {
            item.usePower(player, Math.max(0.5, moved * 0.05), wireless);
        }
        return OptionalInt.of(moved);
    }

    /** 在玩家背包任意槽位找无线终端（含附属终端物品，它们都继承 WirelessTerminalItem）。 */
    private static int findWirelessTerminalSlot(Container inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof WirelessTerminalItem) {
                return i;
            }
        }
        return -1;
    }
}
