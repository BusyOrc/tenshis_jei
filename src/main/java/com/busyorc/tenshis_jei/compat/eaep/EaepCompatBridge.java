package com.busyorc.tenshis_jei.compat.eaep;

import appeng.api.networking.IGrid;
import mezz.jei.common.bookmarks.BookmarkPullTarget;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.fml.ModList;

import java.util.List;
import java.util.OptionalInt;

/**
 * ExtendedAE_Plus 可选前置桥接：本类不引用 EAEP 类，直接引用 EAEP 类的逻辑在
 * EaepCompatImpl（仅在 extendedae_plus 已加载时被加载）。
 * 无线终端定位/连接判定/取物全部复刻 EAEP 的 WirelessTerminalLocator 流程。
 */
public final class EaepCompatBridge {
    private EaepCompatBridge() {
    }

    public static boolean isExtendedAEPlusLoaded() {
        try {
            return ModList.get().isLoaded("extendedae_plus");
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 玩家身上有可用的无线终端（AE2 原版终端 / WTLib 终端 / Curios 饰品槽 / 手持）？ */
    public static boolean hasWirelessTerminal(Player player) {
        return isExtendedAEPlusLoaded() && EaepCompatImpl.hasWirelessTerminal(player);
    }

    /** 服务端枚举无线终端的已连接网络物品清单（未加载/无终端/无连接返回空表）。 */
    public static java.util.List<com.busyorc.tenshis_jei.network.SnapshotDataPacket.Entry> readNetworkEntries(Player player) {
        if (!isExtendedAEPlusLoaded()) {
            return java.util.List.of();
        }
        return EaepCompatImpl.readNetworkEntries(player);
    }

    /** 客户端侧取无线终端的已连接网格（EAEP 定位+连接逻辑；未加载/无终端/未连接返回 empty）。 */
    public static java.util.Optional<IGrid> getConnectedGrid(Player player) {
        if (!isExtendedAEPlusLoaded()) {
            return java.util.Optional.empty();
        }
        return EaepCompatImpl.getConnectedGrid(player);
    }

    /** 用 EAEP 的定位+连接逻辑从 ME 网络拉取，返回实际取到的数量；未加载/无连接/取不到返回 empty。 */
    public static OptionalInt pull(ServerPlayer player, AbstractContainerMenu menu, int containerId, Container playerInventory, List<BookmarkPullTarget> targets) {
        if (!isExtendedAEPlusLoaded()) {
            return OptionalInt.empty();
        }
        return EaepCompatImpl.pull(player, menu, containerId, playerInventory, targets);
    }
}
