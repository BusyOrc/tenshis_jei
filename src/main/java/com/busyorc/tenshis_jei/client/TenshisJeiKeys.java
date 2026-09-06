package com.busyorc.tenshis_jei.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;

/**
 * 本模组的可改键位：生成匠魂工具强化配方链（默认 shift+F）。
 *
 * 通过标准 Forge RegisterKeyMappingsEvent 注册为普通 KeyMapping，玩家可在控制界面改绑；
 * 触发时仍要求按住 SHIFT（与历史行为一致）。提示文本显示的实际键位由
 * {@link #genModifierChainKeyComponent()} 跟随绑定动态生成。
 */
public final class TenshisJeiKeys {
    public static final String CATEGORY = "key.categories.tenshis_jei_addon";

    public static final KeyMapping GEN_MODIFIER_CHAIN = new KeyMapping(
        "key.tenshis_jei_addon.gen_modifier_chain",
        InputConstants.Type.KEYSYM,
        org.lwjgl.glfw.GLFW.GLFW_KEY_F,
        CATEGORY
    );

    private TenshisJeiKeys() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(GEN_MODIFIER_CHAIN);
    }

    /** "SHIFT + <实际键位>"：用于 alt 提示行，随玩家改绑自动变化。 */
    public static Component genModifierChainKeyComponent() {
        InputConstants.Key key = GEN_MODIFIER_CHAIN.getKey();
        String keyName;
        try {
            keyName = key.getDisplayName().getString();
        } catch (Throwable t) {
            keyName = "F";
        }
        if (keyName.isEmpty() || keyName.contains("unknown") || key == InputConstants.UNKNOWN) {
            return Component.literal("SHIFT + F");
        }
        return Component.literal("SHIFT + " + keyName);
    }
}
