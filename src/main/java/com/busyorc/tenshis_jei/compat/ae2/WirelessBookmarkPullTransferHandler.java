package com.busyorc.tenshis_jei.compat.ae2;

import com.busyorc.tenshis_jei.TenshisJeiLog;
import com.busyorc.tenshis_jei.compat.eaep.EaepCompatBridge;
import mezz.jei.common.bookmarks.BookmarkPullTarget;
import mezz.jei.common.bookmarks.ServerBookmarkPullTransferHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.OptionalInt;

/**
 * V/shift+V 拉取配方树物品：玩家身上带 AE2 无线终端（或附属终端物品）时，
 * 不开 ME 终端也能从绑定的网络直接拉物品。
 * <p>
 * 定位/连接/取物全部复用 EAEP（ExtendedAE_Plus）提供的 WirelessTerminalLocator
 * 及其 AE2 菜单宿主逻辑——能用前置模组提供的方法就不自己造轮子。
 * fork 自己的 Ae2BookmarkPullTransferHandler 已在 ME 终端菜单打开时接管；
 * 本 handler 只处理未开终端、EAEP 判定有可用无线连接的情形。
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
        TenshisJeiLog.info("[ET-jei] pull-srv: handler entered, targets=" + targets.size() + ", menu=" + menu.getClass().getName() + ", containerId=" + containerId);
        if (!EaepCompatBridge.isExtendedAEPlusLoaded()) {
            TenshisJeiLog.info("[ET-jei] pull-srv: EAEP not loaded -> wireless pull unavailable");
            return OptionalInt.empty();
        }
        TenshisJeiLog.info("[ET-jei] pull-srv: EAEP loaded, using WirelessTerminalLocator flow");
        return EaepCompatBridge.pull(player, menu, containerId, playerInventory, targets);
    }
}
