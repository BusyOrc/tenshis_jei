package com.busyorc.tenshis_jei.compat.ae2;

import com.busyorc.tenshis_jei.network.WirelessSnapshotCache;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.chain.BookmarkContainerStorageScanner;
import mezz.jei.gui.bookmarks.chain.BookmarkExternalStorageSnapshots;
import appeng.menu.me.common.MEStorageMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * JEI fork 的外部存储快照提供器（BookmarkExternalStorageSnapshots.Provider）：
 * ME 网络数据只存在于服务端，本模组通过 WirelessSnapshotPayloadHandler 定时把
 * 无线终端的已连接网络物品清单同步到客户端缓存（WirelessSnapshotCache），
 * 这里直接读取缓存构建快照。其余拉取逻辑（规划、发包、服务端抽取）完全走 JEI fork。
 * 已打开 ME 终端菜单时返回空（交给 fork 自己的 AE2 提供器）。
 */
public final class WirelessExternalStorageSnapshotProvider implements BookmarkExternalStorageSnapshots.Provider {

    @Override
    public Optional<BookmarkContainerStorageScanner.StorageSnapshot> scan(
        Object menu,
        Object screen,
        Function<ItemStack, Optional<BookmarkIngredientKey>> keyFactory
    ) {
        if (menu instanceof MEStorageMenu) {
            return Optional.empty();
        }
        List<BookmarkExternalStorageSnapshots.Entry> entries = readEntries();
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(BookmarkExternalStorageSnapshots.createSnapshot(entries, keyFactory));
    }

    @Override
    public Optional<List<BookmarkExternalStorageSnapshots.Entry>> readEntries(Object menu) {
        if (menu instanceof MEStorageMenu) {
            return Optional.empty();
        }
        return Optional.of(readEntries());
    }

    /** 从客户端同步缓存构建 fork 的 Entry（代表栈 count=1 + 可用量）。 */
    private static List<BookmarkExternalStorageSnapshots.Entry> readEntries() {
        return WirelessSnapshotCache.getEntries().stream()
            .map(e -> new BookmarkExternalStorageSnapshots.Entry(e.stack(), e.amount()))
            .toList();
    }
}
