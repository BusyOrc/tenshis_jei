package com.busyorc.tenshis_jei.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** S2C：服务端返回的无线网络物品清单（代表栈 count=1 + 可用量）。 */
public record WirelessSnapshotDataPayload(List<Entry> entries) implements CustomPacketPayload {

    public static final Type<WirelessSnapshotDataPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("tenshis_jei", "wireless_snapshot_data"));

    public record Entry(ItemStack stack, long amount) {
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, WirelessSnapshotDataPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public WirelessSnapshotDataPayload decode(RegistryFriendlyByteBuf buf) {
                int count = buf.readVarInt();
                List<Entry> entries = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    long amount = buf.readVarLong();
                    entries.add(new Entry(stack, amount));
                }
                return new WirelessSnapshotDataPayload(entries);
            }
            @Override
            public void encode(RegistryFriendlyByteBuf buf, WirelessSnapshotDataPayload payload) {
                buf.writeVarInt(payload.entries().size());
                for (Entry entry : payload.entries()) {
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, entry.stack());
                    buf.writeVarLong(entry.amount());
                }
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
