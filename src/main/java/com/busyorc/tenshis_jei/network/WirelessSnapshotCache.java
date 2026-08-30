package com.busyorc.tenshis_jei.network;

import java.util.List;

/** 客户端缓存：服务端返回的无线网络物品清单（供快照提供器读取）。 */
public final class WirelessSnapshotCache {
    private WirelessSnapshotCache() {
    }

    private static volatile List<WirelessSnapshotDataPayload.Entry> entries = List.of();
    private static volatile long lastUpdated = 0L;

    public static void update(List<WirelessSnapshotDataPayload.Entry> newEntries) {
        entries = List.copyOf(newEntries);
        lastUpdated = System.currentTimeMillis();
    }

    public static List<WirelessSnapshotDataPayload.Entry> getEntries() {
        return entries;
    }

    public static long getLastUpdated() {
        return lastUpdated;
    }
}
