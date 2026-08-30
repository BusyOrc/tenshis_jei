package com.busyorc.tenshis_jei.compat.curios;

import appeng.items.tools.powered.WirelessTerminalItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/** Curios 槽位扫描实现（仅 curios 加载时由桥接类加载）。 */
final class CuriosCompatImpl {
    private CuriosCompatImpl() {
    }

    static boolean hasWirelessTerminal(@Nullable Player player) {
        return findWirelessTerminal(player) != null;
    }

    @Nullable
    static CuriosCompatBridge.CuriosTerminalRef findWirelessTerminal(@Nullable Player player) {
        if (player == null) {
            return null;
        }
        try {
            var opt = CuriosApi.getCuriosInventory(player);
            if (!opt.isPresent()) {
                return null;
            }
            ICuriosItemHandler handler = opt.get();
            for (var entry : handler.getCurios().entrySet()) {
                String slotId = entry.getKey();
                ICurioStacksHandler stacksHandler = entry.getValue();
                IDynamicStackHandler stacks = stacksHandler.getStacks();
                int slots = stacks.getSlots();
                for (int i = 0; i < slots; i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    if (!stack.isEmpty() && stack.getItem() instanceof WirelessTerminalItem) {
                        return new CuriosCompatBridge.CuriosTerminalRef(slotId, i, stack);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
