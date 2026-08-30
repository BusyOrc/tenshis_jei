package com.busyorc.tenshis_jei.compat.curios;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * Curios 可选前置桥接：本类不引用 Curios 类，直接引用 Curios 类的逻辑在 CuriosCompatImpl
 * （仅在 curios 已加载时被加载）。AE2 是硬依赖，可直接引用。
 */
public final class CuriosCompatBridge {
    private CuriosCompatBridge() {
    }

    public static boolean isCuriosLoaded() {
        try {
            return ModList.get().isLoaded("curios");
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 玩家任一 Curios 槽位有无线终端？ */
    public static boolean hasWirelessTerminal(Player player) {
        return isCuriosLoaded() && CuriosCompatImpl.hasWirelessTerminal(player);
    }

    /** 在 Curios 槽位中找无线终端，返回槽位引用；未找到或未加载 Curios 时返回 null。 */
    @Nullable
    public static CuriosTerminalRef findWirelessTerminal(Player player) {
        if (!isCuriosLoaded()) {
            return null;
        }
        return CuriosCompatImpl.findWirelessTerminal(player);
    }

    public record CuriosTerminalRef(String slotId, int index, ItemStack stack) {
        public CuriosTerminalRef {
            stack = stack.copy();
        }
    }
}
