package com.busyorc.tenshis_jei.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S：客户端请求服务端枚举无线终端的已连接 ME 网络物品清单。 */
public record WirelessSnapshotRequestPayload() implements CustomPacketPayload {

    public static final Type<WirelessSnapshotRequestPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("tenshis_jei_addon", "wireless_snapshot_request"));

    public static final StreamCodec<ByteBuf, WirelessSnapshotRequestPayload> STREAM_CODEC =
        StreamCodec.unit(new WirelessSnapshotRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
