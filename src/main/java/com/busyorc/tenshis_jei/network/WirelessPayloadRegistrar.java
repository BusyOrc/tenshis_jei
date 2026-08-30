package com.busyorc.tenshis_jei.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** 无线网络快照同步的 payload 注册 + 客户端定时刷新。 */
public final class WirelessPayloadRegistrar {
    private WirelessPayloadRegistrar() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("tenshis_jei");
        registrar.playToServer(WirelessSnapshotRequestPayload.TYPE, WirelessSnapshotRequestPayload.STREAM_CODEC, WirelessSnapshotPayloadHandler::handleServer);
        registrar.playToClient(WirelessSnapshotDataPayload.TYPE, WirelessSnapshotDataPayload.STREAM_CODEC, WirelessSnapshotPayloadHandler::handleClient);
    }

    private static int tickCounter = 0;

    public static void onClientTick(ClientTickEvent.Post event) {
        if ((++tickCounter & 31) != 0) {
            return; // 每 32 tick（约 1.6 秒）请求一次
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen == null) {
            return;
        }
        try {
            player.connection.send(new WirelessSnapshotRequestPayload());
        } catch (Throwable ignored) {
        }
    }
}
