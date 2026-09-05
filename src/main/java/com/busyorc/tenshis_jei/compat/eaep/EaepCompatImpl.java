package com.busyorc.tenshis_jei.compat.eaep;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
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

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    static java.util.List<com.busyorc.tenshis_jei.network.WirelessSnapshotDataPayload.Entry> readNetworkEntries(Player player) {
        try {
            WirelessTerminalLocator.LocatedTerminal located = WirelessTerminalLocator.find(player);
            if (located.isEmpty()) {
                return java.util.List.of();
            }
            IGrid grid = WirelessTerminalLocator.getConnectedGrid(player, located);
            if (grid == null) {
                return java.util.List.of();
            }
            java.util.List<com.busyorc.tenshis_jei.network.WirelessSnapshotDataPayload.Entry> entries = new java.util.ArrayList<>();
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
                    entries.add(new com.busyorc.tenshis_jei.network.WirelessSnapshotDataPayload.Entry(representative, amount));
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
            // 每次拉取内，同一物品只触发一次自动合成
            Set<AEItemKey> autoCrafted = new HashSet<>();
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
                    if (extracted < amount) {
                        // 库存不足：对缺口触发 AE2 自动合成（覆盖"无配方树/简单收藏"场景）
                        long shortfall = amount - extracted;
                        if (autoCrafted.add(key)) {
                            triggerAutoCraft(grid, player, key, shortfall, actionSource);
                        }
                    }
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

    /** 服务端：EAEP 定位+连接，对给定清单逐个触发自动合成。 */
    static void autoCraft(ServerPlayer player, List<com.busyorc.tenshis_jei.network.WirelessSnapshotDataPayload.Entry> entries) {
        try {
            WirelessTerminalLocator.LocatedTerminal located = WirelessTerminalLocator.find(player);
            if (located.isEmpty()) {
                return;
            }
            IGrid grid = WirelessTerminalLocator.getConnectedGrid(player, located);
            if (grid == null) {
                return;
            }
            IActionSource src = IActionSource.ofPlayer(player);
            for (com.busyorc.tenshis_jei.network.WirelessSnapshotDataPayload.Entry entry : entries) {
                if (entry.amount() <= 0) {
                    continue;
                }
                AEItemKey key = AEItemKey.of(entry.stack());
                if (key != null) {
                    triggerAutoCraft(grid, player, key, entry.amount(), src);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 用 AE2 公开 ICraftingService 触发自动合成。
     * 1) 没有样板的物品先 isCraftable 跳过；
     * 2) simRequester 必须返回网格 pivot 节点，否则合成计算拿不到库存、plan.simulation()==true；
     * 3) 计算是异步的，不能在主线程 job.get() 阻塞——后台线程等结果 + 主线程 submitJob。
     */
    private static void triggerAutoCraft(IGrid grid, Player player, AEItemKey what, long amount, IActionSource src) {
        try {
            ICraftingService crafting = grid.getCraftingService();
            if (crafting == null) {
                com.busyorc.tenshis_jei.TenshisJeiLog.info("[ET-jei] auto-craft: no crafting service");
                return;
            }
            if (!crafting.isCraftable(what)) {
                com.busyorc.tenshis_jei.TenshisJeiLog.info("[ET-jei] auto-craft skip " + what + " (no pattern)");
                return;
            }
            IGridNode pivot = grid.getPivot();
            ICraftingSimulationRequester simRequester = new ICraftingSimulationRequester() {
                @Override
                public IActionSource getActionSource() {
                    return src;
                }
                @Override
                public IGridNode getGridNode() {
                    return pivot;
                }
            };
            Future<ICraftingPlan> job = crafting.beginCraftingCalculation(
                player.level(),
                simRequester,
                what,
                amount,
                CalculationStrategy.CRAFT_LESS
            );
            var server = player.getServer();
            Thread waiter = new Thread(() -> {
                try {
                    ICraftingPlan plan = job.get(30, TimeUnit.SECONDS);
                    if (plan == null || plan.simulation()) {
                        com.busyorc.tenshis_jei.TenshisJeiLog.info("[ET-jei] auto-craft " + what + " x" + amount + " -> plan not craftable (simulation)");
                        return;
                    }
                    if (server != null) {
                        server.execute(() -> {
                            try {
                                var result = crafting.submitJob(plan, null, null, true, src);
                                boolean ok = result != null && result.successful();
                                com.busyorc.tenshis_jei.TenshisJeiLog.info("[ET-jei] auto-craft " + what + " x" + amount + " -> " + (ok ? "submitted" : "failed"));
                            } catch (Throwable t) {
                                com.busyorc.tenshis_jei.TenshisJeiLog.info("[ET-jei] auto-craft submit threw: " + t);
                            }
                        });
                    }
                } catch (Throwable t) {
                    com.busyorc.tenshis_jei.TenshisJeiLog.info("[ET-jei] auto-craft calc threw/timeout: " + t);
                }
            }, "tenshis-auto-craft");
            waiter.setDaemon(true);
            waiter.start();
        } catch (Throwable t) {
            com.busyorc.tenshis_jei.TenshisJeiLog.info("[ET-jei] auto-craft threw: " + t);
        }
    }
}
