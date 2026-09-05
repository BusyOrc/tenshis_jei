package com.busyorc.tenshis_jei.network;

import com.busyorc.tenshis_jei.compat.eaep.EaepCompatBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;
import java.util.function.Supplier;

/** Forge 1.20.1 SimpleChannel for wireless ME network snapshot sync. */
public final class WirelessNetworkHandler {
    private static final String PROTOCOL_VERSION = "1";

    public static final ResourceLocation CHANNEL_ID = new ResourceLocation("tenshis_jei_addon", "wireless_snapshot");

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        CHANNEL_ID,
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    private static int tickCounter = 0;
    private static boolean registered = false;

    private WirelessNetworkHandler() {
    }

    public static void register(IEventBus modEventBus) {
        if (registered) {
            return;
        }
        registered = true;
        CHANNEL.registerMessage(0, RequestSnapshotPacket.class,
            RequestSnapshotPacket::encode,
            RequestSnapshotPacket::decode,
            WirelessNetworkHandler::handleRequest
        );
        CHANNEL.registerMessage(1, SnapshotDataPacket.class,
            SnapshotDataPacket::encode,
            SnapshotDataPacket::decode,
            WirelessNetworkHandler::handleData
        );
        CHANNEL.registerMessage(2, CraftRequestPacket.class,
            CraftRequestPacket::encode,
            CraftRequestPacket::decode,
            WirelessNetworkHandler::handleCraftRequest
        );
    }

    /** Server side: client asked for the wireless network contents -> reply to that player. */
    private static void handleRequest(RequestSnapshotPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                List<SnapshotDataPacket.Entry> entries = EaepCompatBridge.readNetworkEntries(player);
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SnapshotDataPacket(entries));
            }
        });
        context.setPacketHandled(true);
    }

    /** Client side: store the received network contents into the cache. */
    private static void handleData(SnapshotDataPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> WirelessSnapshotCache.update(msg.entries()));
        context.setPacketHandled(true);
    }

    /** Server side: client asked to auto-craft the given items (EAEP locates + grid + submit). */
    private static void handleCraftRequest(CraftRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                EaepCompatBridge.autoCraft(player, msg.entries());
            }
        });
        context.setPacketHandled(true);
    }

    /** Client: ask the server to auto-craft these items (used by the pull-shortfall mixin). */
    public static void sendCraftRequest(List<SnapshotDataPacket.Entry> entries) {
        try {
            CHANNEL.sendToServer(new CraftRequestPacket(entries));
        } catch (Throwable ignored) {
        }
    }

    /** Client: periodic refresh request while a screen is open. */
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if ((++tickCounter & 31) != 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen == null) {
            return;
        }
        try {
            CHANNEL.sendToServer(new RequestSnapshotPacket());
        } catch (Throwable ignored) {
        }
    }
}
