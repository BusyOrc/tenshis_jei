package com.busyorc.tenshis_jei.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** S2C: server returns the wireless ME network contents (representative stack count=1 + amount). */
public record SnapshotDataPacket(List<Entry> entries) {
    public record Entry(ItemStack stack, long amount) {
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (Entry e : entries) {
            buf.writeItem(e.stack());
            buf.writeVarLong(e.amount());
        }
    }

    public static SnapshotDataPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ItemStack stack = buf.readItem();
            long amount = buf.readVarLong();
            entries.add(new Entry(stack, amount));
        }
        return new SnapshotDataPacket(entries);
    }
}
