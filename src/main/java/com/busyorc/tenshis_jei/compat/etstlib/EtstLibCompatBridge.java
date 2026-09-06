package com.busyorc.tenshis_jei.compat.etstlib;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.Optional;
import java.util.UUID;

/**
 * EtST-Lib 工具 UUID 只读桥。EtST-Lib 会在匠魂工具合成/制造时把 UUID 写入工具 NBT 的
 * "etstlib_tool_uuid" 键；本模组只读取该 UUID 用于在自动强化流程中跨步骤追踪"同一把工具"。
 */
public final class EtstLibCompatBridge {
    private EtstLibCompatBridge() {
    }

    public static final String KEY_TOOL_UUID = "etstlib_tool_uuid";

    public static boolean isEtstLibLoaded() {
        try {
            return ModList.get().isLoaded("etstlib");
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 读工具 UUID。EtST-Lib 未给该工具 UUID（或未加载 etstlib）时返回 empty。 */
    public static Optional<UUID> getToolUuid(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(KEY_TOOL_UUID, net.minecraft.nbt.Tag.TAG_STRING)) {
            try {
                return Optional.of(UUID.fromString(tag.getString(KEY_TOOL_UUID)));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
