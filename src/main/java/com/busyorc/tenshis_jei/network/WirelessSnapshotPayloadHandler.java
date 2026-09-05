package com.busyorc.tenshis_jei.network;

import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.busyorc.tenshis_jei.compat.eaep.EaepCompatBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** 无线网络快照同步的收发处理：服务端用 EAEP 定位+枚举，客户端写入缓存。 */
public final class WirelessSnapshotPayloadHandler {
    private WirelessSnapshotPayloadHandler() {
    }

    public static void handleServer(WirelessSnapshotRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                if (!(context.player() instanceof ServerPlayer player)) {
                    return;
                }
                List<WirelessSnapshotDataPayload.Entry> entries =
                    com.busyorc.tenshis_jei.compat.eaep.EaepCompatBridge.readNetworkEntries(player);
                context.reply(new WirelessSnapshotDataPayload(entries));
            } catch (Throwable t) {
                context.reply(new WirelessSnapshotDataPayload(List.of()));
            }
        });
    }

    public static void handleCraftRequest(CraftRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                if (!(context.player() instanceof ServerPlayer player)) {
                    return;
                }
                com.busyorc.tenshis_jei.compat.eaep.EaepCompatBridge.autoCraft(player, payload.entries());
            } catch (Throwable ignored) {
            }
        });
    }

    public static void handleClient(WirelessSnapshotDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> WirelessSnapshotCache.update(payload.entries()));
    }
}
