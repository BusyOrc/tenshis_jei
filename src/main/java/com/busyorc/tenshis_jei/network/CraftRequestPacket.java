package com.busyorc.tenshis_jei.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** C2S：客户端告诉服务端要自动合成的物品清单（代表栈 count=1 + 数量）。 */
public record CraftRequestPacket(List<SnapshotDataPacket.Entry> entries) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (SnapshotDataPacket.Entry e : entries) {
            buf.writeItem(e.stack());
            buf.writeVarLong(e.amount());
        }
    }

    public static CraftRequestPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<SnapshotDataPacket.Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ItemStack stack = buf.readItem();
            long amount = buf.readVarLong();
            entries.add(new SnapshotDataPacket.Entry(stack, amount));
        }
        return new CraftRequestPacket(entries);
    }
}
