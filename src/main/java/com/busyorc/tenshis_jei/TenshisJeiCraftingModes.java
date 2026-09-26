package com.busyorc.tenshis_jei;

/**
 * Reads the recipe-tree crafting mode from the config in a defensive way:
 * if the config is not loaded yet (or failed to load), falls back to EXACT
 * (the new default logic) instead of throwing IllegalStateException.
 */
public final class TenshisJeiCraftingModes {
    /**
     * 仅在"本模组负责的终端（ET / WCWT）正在派发配方树合成"这段时间内为 true。
     * 该窗口内，JEIU 的 BookmarkCraftingGridFill 数量封顶会被改成按"材料实际可用量"计算，
     * 而不是按"槽位数 × 最大堆叠"——因为终端由我们在服务端逐份填料合成，不存在格子上限。
     */
    private static boolean terminalFillWindow;

    private TenshisJeiCraftingModes() {
    }

    public static void setTerminalFillWindow(boolean value) {
        terminalFillWindow = value;
    }

    public static boolean isTerminalFillWindow() {
        return terminalFillWindow;
    }

    /** 当前打开的容器菜单是否为本模组支持的终端（ET 或 WCWT）。 */
    public static boolean isSupportedTerminalMenuOpen() {
        try {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft.player == null || minecraft.player.containerMenu == null) {
                return false;
            }
            net.minecraft.world.inventory.AbstractContainerMenu menu = minecraft.player.containerMenu;
            if (menu instanceof me.myogoo.extendedterminal.menu.extendedterminal.ETTerminalMenu) {
                return true;
            }
            try {
                if (net.neoforged.fml.ModList.get().isLoaded("wcwt")) {
                    String target = "com.lhy.wcwt.menu.WirelessComprehensiveWorkTerminalMenu";
                    for (Class<?> c = menu.getClass(); c != null; c = c.getSuperclass()) {
                        if (target.equals(c.getName())) {
                            return true;
                        }
                    }
                }
            } catch (Throwable ignored) {
                // WCWT 未安装/类加载失败：忽略
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isExactTreeQuantityEnabled() {
        try {
            return TenshisJeiConfig.RECIPE_TREE_CRAFTING_MODE.get() == RecipeTreeCraftingMode.EXACT;
        } catch (IllegalStateException e) {
            return true;
        }
    }
}
