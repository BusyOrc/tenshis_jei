package com.busyorc.tenshis_jei.mixin;

import com.busyorc.tenshis_jei.client.TenshisJeiKeys;
import com.busyorc.tenshis_jei.compat.tinkers.TinkersCompatBridge;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.gui.overlay.elements.IngredientBookmarkElement;
import mezz.jei.gui.overlay.ingredients.IngredientGridTooltipHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 JEI 自带的"按住 ALT 显示快捷键"收藏物品提示里追加一行：
 *   <实际键位> - 生成匠魂工具强化的配方链
 * 只对"收藏的匠魂工具"显示，键位跟随 TenshisJeiKeys.GEN_MODIFIER_CHAIN 的实际绑定（默认 shift+F）。
 */
@Mixin(value = IngredientBookmarkElement.class, remap = false)
public abstract class IngredientBookmarkElementGenChainHotkeyMixin {

    @Inject(
        method = "getTooltip(Lmezz/jei/common/gui/JeiTooltip;Lmezz/jei/gui/overlay/ingredients/IngredientGridTooltipHelper;Lmezz/jei/api/ingredients/IIngredientRenderer;Lmezz/jei/api/ingredients/IIngredientHelper;)V",
        at = @At("RETURN"),
        remap = false
    )
    private void tenshisJei$appendGenChainHotkey(
        IngredientBookmarkElement<?> self,
        JeiTooltip tooltip,
        IngredientGridTooltipHelper tooltipHelper,
        IIngredientRenderer<?> ingredientRenderer,
        IIngredientHelper<?> ingredientHelper,
        CallbackInfo ci
    ) {
        try {
            if (!Screen.hasAltDown()) {
                return;
            }
            ITypedIngredient<?> typed = self.getTypedIngredient();
            if (typed == null || typed.getIngredient() == null) {
                return;
            }
            Object obj = typed.getIngredient();
            if (!(obj instanceof ItemStack stack) || !TinkersCompatBridge.isTinkersTool(stack)) {
                return;
            }
            tooltip.add(
                Component.literal("")
                    .append(TenshisJeiKeys.genModifierChainKeyComponent().copy().withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW))
                    .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                    .append(Component.translatable("tenshis_jei_addon.hotkey.gen_modifier_chain").withStyle(ChatFormatting.GRAY))
            );
        } catch (Throwable t) {
            // 提示是锦上添花，失败不影响其它功能
        }
    }
}
