package com.busyorc.tenshis_jei.compat.wcwt;

import com.busyorc.tenshis_jei.compat.et.EtLog;
import com.busyorc.tenshis_jei.compat.wcwt.client.WcwtCraftingGridTargetSlotProvider;
import mezz.jei.common.bookmarks.CraftingGridCraftExecutors;
import mezz.jei.common.bookmarks.CraftingGridFillExecutors;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkGhostOverlayTargetSlots;

/** 直接引用 WCWT 类型的注册实现；只会在 WCWT 已加载时由 {@link WcwtCompat} 调用。 */
final class WcwtCompatImpl {
    private WcwtCompatImpl() {
    }

    static void registerServer() {
        CraftingGridCraftExecutors.registerExecutor(new WcwtCraftingGridCraftExecutor());
        CraftingGridFillExecutors.registerExecutor(new WcwtCraftingGridFillExecutor());
        EtLog.info("[ET-jei] registered WCWT terminal executors: craft + fill");
    }

    static void registerClient() {
        BookmarkGhostOverlayTargetSlots.registerProvider(new WcwtCraftingGridTargetSlotProvider());
        EtLog.info("[ET-jei] registered WCWT client providers: targetSlots");
    }
}
