package com.busyorc.tenshis_jei.network;

import net.minecraft.network.FriendlyByteBuf;

/** C2S: client asks the server to enumerate the wireless ME network contents. */
public record RequestSnapshotPacket() {
    public static void encode(RequestSnapshotPacket p, FriendlyByteBuf buf) {
    }
    public static RequestSnapshotPacket decode(FriendlyByteBuf buf) {
        return new RequestSnapshotPacket();
    }
}
