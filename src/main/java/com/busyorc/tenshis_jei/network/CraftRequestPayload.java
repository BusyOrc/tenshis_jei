package com.busyorc.tenshis_jei.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** C2S：客户端告诉服务端要自动合成的物品清单（代表栈 count=1 + 数量）。 */
public record CraftRequestPayload(List<WirelessSnapshotDataPayload.Entry> entries) implements CustomPacketPayload {

    public static final Type<CraftRequestPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("tenshis_jei_addon", "craft_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftRequestPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public CraftRequestPayload decode(RegistryFriendlyByteBuf buf) {
                int count = buf.readVarInt();
                List<WirelessSnapshotDataPayload.Entry> entries = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    long amount = buf.readVarLong();
                    entries.add(new WirelessSnapshotDataPayload.Entry(stack, amount));
                }
                return new CraftRequestPayload(entries);
            }
            @Override
            public void encode(RegistryFriendlyByteBuf buf, CraftRequestPayload payload) {
                buf.writeVarInt(payload.entries().size());
                for (WirelessSnapshotDataPayload.Entry entry : payload.entries()) {
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
