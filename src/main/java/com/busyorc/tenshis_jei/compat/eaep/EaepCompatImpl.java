package com.busyorc.tenshis_jei.compat.eaep;

import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import com.extendedae_plus.util.wireless.WirelessTerminalLocator;
import mezz.jei.common.bookmarks.BookmarkPullTarget;
import mezz.jei.common.bookmarks.ServerBookmarkExternalStoragePull;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/** EAEP 无线实现（仅 extendedae_plus 加载时由桥接类触发加载；AE2 是硬依赖）。 */
final class EaepCompatImpl {
    private EaepCompatImpl() {
    }

    static boolean hasWirelessTerminal(Player player) {
        try {
            return !WirelessTerminalLocator.find(player).isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    static java.util.List<com.busyorc.tenshis_jei.network.SnapshotDataPacket.Entry> readNetworkEntries(Player player) {
        try {
            WirelessTerminalLocator.LocatedTerminal located = WirelessTerminalLocator.find(player);
            if (located.isEmpty()) {
                return java.util.List.of();
            }
            IGrid grid = WirelessTerminalLocator.getConnectedGrid(player, located);
            if (grid == null) {
                return java.util.List.of();
            }
            java.util.List<com.busyorc.tenshis_jei.network.SnapshotDataPacket.Entry> entries = new java.util.ArrayList<>();
            MEStorage storage = grid.getStorageService().getInventory();
            for (var entry : storage.getAvailableStacks()) {
                if (!(entry.getKey() instanceof AEItemKey itemKey)) {
                    continue;
                }
                long amount = entry.getLongValue();
                if (amount <= 0) {
                    continue;
                }
                ItemStack representative = itemKey.toStack(1);
                if (!representative.isEmpty()) {
                    entries.add(new com.busyorc.tenshis_jei.network.SnapshotDataPacket.Entry(representative, amount));
                }
            }
            return entries;
        } catch (Throwable ignored) {
            return java.util.List.of();
        }
    }

    static Optional<IGrid> getConnectedGrid(Player player) {
        try {
            WirelessTerminalLocator.LocatedTerminal located = WirelessTerminalLocator.find(player);
            if (located.isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(WirelessTerminalLocator.getConnectedGrid(player, located));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    static OptionalInt pull(
        ServerPlayer player,
        AbstractContainerMenu menu,
        int containerId,
        Container playerInventory,
        List<BookmarkPullTarget> targets
    ) {
        try {
            WirelessTerminalLocator.LocatedTerminal located = WirelessTerminalLocator.find(player);
            if (located.isEmpty()) {
                com.busyorc.tenshis_jei.TenshisJeiLog.info("[ET-jei] pull-srv: EAEP find -> no wireless terminal on player");
                return OptionalInt.empty();
            }
            com.busyorc.tenshis_jei.TenshisJeiLog.info("[ET-jei] pull-srv: EAEP find -> " + located.getSlotIndex() + " slotIndex" + (located.getCuriosSlotId() != null ? ", curios=" + located.getCuriosSlotId() + "#" + located.getCuriosIndex() : "") + ", stack=" + located.stack);
            IGrid grid = WirelessTerminalLocator.getConnectedGrid(player, located);
            if (grid == null) {
                com.busyorc.tenshis_jei.TenshisJeiLog.info("[ET-jei] pull-srv: EAEP getConnectedGrid -> null (out of range / unlinked / dead battery)");
                return OptionalInt.empty();
            }
            com.busyorc.tenshis_jei.TenshisJeiLog.info("[ET-jei] pull-srv: EAEP getConnectedGrid -> grid OK");
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
                        com.busyorc.tenshis_jei.TenshisJeiLog.info("[ET-jei] pull-srv: target " + target.itemStack() + " has no AEItemKey -> skip");
                        return ItemStack.EMPTY;
                    }
                    long extracted = StorageHelper.poweredExtraction(energy, storage, key, amount, actionSource);
                    com.busyorc.tenshis_jei.TenshisJeiLog.info("[ET-jei] pull-srv: extract " + key + " amount=" + amount + " -> extracted " + extracted);
                    if (extracted <= 0) {
                        return ItemStack.EMPTY;
                    }
                    ItemStack extractedStack = target.itemStack().copy();
                    extractedStack.setCount((int) Math.min(extracted, Integer.MAX_VALUE));
                    return extractedStack;
                }
            );
            com.busyorc.tenshis_jei.TenshisJeiLog.info("[ET-jei] pull-srv: moved = " + moved);
            if (moved > 0) {
                WirelessTerminalLocator.useTerminalPower(player, located, Math.max(0.5, moved * 0.05));
                located.commit();
            }
            return OptionalInt.of(moved);
        } catch (Throwable t) {
            com.busyorc.tenshis_jei.TenshisJeiLog.info("[ET-jei] pull-srv: EAEP flow threw: " + t);
            return OptionalInt.empty();
        }
    }
}
