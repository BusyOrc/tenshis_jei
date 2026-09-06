package com.busyorc.tenshis_jei.compat.tinkers;

import com.busyorc.tenshis_jei.TenshisJeiLog;
import com.busyorc.tenshis_jei.compat.etstlib.EtstLibCompatBridge;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingRunner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * 砧自动强化：工具往返 slot0 的自动回槽会话。
 *
 * fork 的 auto-crafting Task 每次把工具 shift 出砧的结果槽（工具进玩家物品栏、slot0 清空）。
 * 本会话在 fork Task 运行期间每 tick：slot0 为空时用 {@link MultiPlayerGameMode#handleInventoryMouseClick}
 * 把该 UUID 的工具真正放回 slot0（该 API 才给服务端发包；menu.clicked 只改客户端会造成不同步）。
 *
 * 会话存活以 fork 的 {@link BookmarkAutoCraftingRunner#hasActiveTask()} 为准：
 * - 任务活着 -> 持续回槽，保证下一个 modifier 能连续施加；
 * - 任务结束（整条配方链完成）-> 若工具因流程被我们放回 slot0，则送回玩家物品栏，然后结束会话，
 *   不再让它滞留在 slot0（用户手动拿走也不会再被拉回）。
 */
public final class AnvilAutoReSlot {
    /** slot0 持续为空超过多少 tick 视为异常（工具丢失/流程卡死），放行原确认以免死锁。 */
    private static final int EMPTY_ABORT_TICKS = 300; // 15s

    private static UUID activeUuid;
    private static BookmarkAutoCraftingRunner forkRunner;
    private static boolean weReslotted;
    private static int emptyTicks;
    private static boolean logged;

    private AnvilAutoReSlot() {
    }

    public static boolean isActive() {
        return activeUuid != null;
    }

    /** fork Task 成功启动后调用，记录要守护的工具与 fork 运行器。 */
    public static void begin(UUID uuid, BookmarkAutoCraftingRunner runner) {
        activeUuid = uuid;
        forkRunner = runner;
        weReslotted = false;
        emptyTicks = 0;
        logged = false;
        TenshisJeiLog.info("[ET-jei] anvil-reslot: session begin uuid=" + uuid);
    }

    public static void end() {
        if (activeUuid != null) {
            TenshisJeiLog.info("[ET-jei] anvil-reslot: session end");
        }
        activeUuid = null;
        forkRunner = null;
        weReslotted = false;
        emptyTicks = 0;
        logged = false;
    }

    /** fork 门控用：当前打开的砧菜单 slot0 是否已放回我们的工具（反映服务端真实状态）。 */
    public static boolean toolBackInSlot0() {
        if (activeUuid == null) {
            return false;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || !(mc.screen instanceof AbstractContainerScreen<?> cs)) {
                return false;
            }
            AbstractContainerMenu menu = cs.getMenu();
            boolean tinker = menu instanceof slimeknights.tconstruct.tables.menu.TinkerStationContainerMenu
                || menu instanceof slimeknights.tconstruct.tables.menu.CraftingStationContainerMenu;
            if (!tinker || menu.slots.isEmpty()) {
                return false;
            }
            ItemStack inSlot = menu.getSlot(0).getItem();
            if (inSlot.isEmpty()) {
                return false;
            }
            return EtstLibCompatBridge.getToolUuid(inSlot).map(u -> u.equals(activeUuid)).orElse(false);
        } catch (Throwable t) {
            return false;
        }
    }

    /** fork 门控用：工具迟迟不回槽（异常）时放行原确认，避免 Task 永久等待。 */
    public static boolean emptyForTooLong() {
        return emptyTicks > EMPTY_ABORT_TICKS;
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (activeUuid == null) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.gameMode == null) {
                end();
                return;
            }
            if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
                end();
                return;
            }
            AbstractContainerMenu menu = cs.getMenu();
            boolean tinker = menu instanceof slimeknights.tconstruct.tables.menu.TinkerStationContainerMenu
                || menu instanceof slimeknights.tconstruct.tables.menu.CraftingStationContainerMenu;
            if (!tinker) {
                // 砧界面已关：槽位物品会自动归位，直接结束。
                end();
                return;
            }
            if (menu.slots.isEmpty()) {
                return;
            }
            boolean forkAlive = forkRunner != null && forkRunner.hasActiveTask();
            if (!forkAlive) {
                // 整条链完成：若工具因流程被回槽在 slot0，送回物品栏；结束会话。
                finalizeAndEnd(menu, mc);
                return;
            }
            // fork 任务存活：持续维护工具在 slot0
            Slot slot0 = menu.getSlot(0);
            if (slot0 == null) {
                return;
            }
            ItemStack inSlot = slot0.getItem();
            if (!inSlot.isEmpty()) {
                boolean ours = EtstLibCompatBridge.getToolUuid(inSlot)
                    .map(u -> u.equals(activeUuid)).orElse(false);
                if (ours) {
                    emptyTicks = 0;
                } else {
                    end();
                }
                return;
            }
            // slot0 为空 -> 尝试把我们的工具从玩家物品栏真正放回 slot0
            emptyTicks++;
            MultiPlayerGameMode gm = mc.gameMode;
            for (int idx = 0; idx < menu.slots.size(); idx++) {
                Slot s = menu.slots.get(idx);
                if (s == null || s.container != mc.player.getInventory()) {
                    continue;
                }
                ItemStack stack = s.getItem();
                if (stack.isEmpty()) {
                    continue;
                }
                Optional<UUID> u = EtstLibCompatBridge.getToolUuid(stack);
                if (u.isEmpty() || !u.get().equals(activeUuid)) {
                    continue;
                }
                gm.handleInventoryMouseClick(menu.containerId, idx, 0, ClickType.PICKUP, mc.player);
                gm.handleInventoryMouseClick(menu.containerId, 0, 0, ClickType.PICKUP, mc.player);
                weReslotted = true;
                emptyTicks = 0;
                if (!logged) {
                    TenshisJeiLog.info("[ET-jei] anvil-reslot: moved tool from menu-slot " + idx + " back to slot0 (server click)");
                    logged = true;
                }
                return;
            }
            // 工具既不在 slot0 也不在物品栏（服务端仍在处理/未同步），本 tick 等待。
        } catch (Throwable t) {
            TenshisJeiLog.info("[ET-jei] anvil-reslot threw: " + t);
        }
    }

    /** 链完成收尾：只有"流程曾把工具从物品栏移回过 slot0"时才把它送回物品栏（用户自己放的工具不动）。 */
    private static void finalizeAndEnd(AbstractContainerMenu menu, Minecraft mc) {
        try {
            if (weReslotted && !menu.slots.isEmpty()) {
                Slot slot0 = menu.getSlot(0);
                if (slot0 != null && !slot0.getItem().isEmpty()) {
                    ItemStack inSlot = slot0.getItem();
                    boolean ours = EtstLibCompatBridge.getToolUuid(inSlot)
                        .map(u -> u.equals(activeUuid)).orElse(false);
                    if (ours) {
                        // 找一个空的玩家物品栏槽位放回工具
                        Integer emptyIdx = null;
                        for (int idx = 0; idx < menu.slots.size(); idx++) {
                            Slot s = menu.slots.get(idx);
                            if (s != null && s.container == mc.player.getInventory() && s.getItem().isEmpty()) {
                                emptyIdx = idx;
                                break;
                            }
                        }
                        if (emptyIdx != null) {
                            MultiPlayerGameMode gm = mc.gameMode;
                            gm.handleInventoryMouseClick(menu.containerId, 0, 0, ClickType.PICKUP, mc.player);
                            gm.handleInventoryMouseClick(menu.containerId, emptyIdx, 0, ClickType.PICKUP, mc.player);
                            TenshisJeiLog.info("[ET-jei] anvil-reslot: chain done -> tool returned to inventory slot " + emptyIdx);
                        } else {
                            TenshisJeiLog.info("[ET-jei] anvil-reslot: chain done but inventory full, tool left in slot0");
                        }
                    }
                }
            }
        } catch (Throwable t) {
            TenshisJeiLog.info("[ET-jei] anvil-reslot finalize threw: " + t);
        }
        end();
    }
}
