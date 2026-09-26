package com.busyorc.tenshis_jei.mixin;

import com.busyorc.tenshis_jei.compat.et.EtLog;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.input.handlers.IngredientTagSelectionTooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 与 tooltip-overhaul 的冲突修复：
 * <p>
 * tooltip-overhaul 在 {@code GuiGraphics.renderTooltipInternal} 的 HEAD 注入并在自家渲染完成后
 * {@code ci.cancel()}，整条 vanilla tooltip 管线被跳过 → JEIU
 * {@code IngredientTagSelectionTooltip} 里的 {@code ClientTooltipPositioner} 永远不会被回调，
 * 它用来做**点击命中**的 {@code area} 矩形就只剩 show() 时按内容估算的尺寸/位置，而实际框由
 * tooltip-overhaul 用自己的内边距/布局/边距适配（乃至缩放）画在别处 → 出现"点击位置与渲染框错开"。
 * <p>
 * 修复思路（只在检测到 tooltip-overhaul 时生效，其它 tooltip 一律不动）：
 * 该标签面板不再走 tooltip 管线，改为在本类原 {@code draw(...)} 里**自行绘制**：
 * 屏幕变暗 + 深色背景 + 复用 JEIU 自己的 {@code renderImage(...)}（白边框/标题/标签行/悬停高亮/滚动条）。
 * 这样"画出来的矩形"与"命中判定用的 area"必然是同一个矩形，任何模组改 tooltip 管线都不会再错位。
 */
@Mixin(value = IngredientTagSelectionTooltip.class, remap = false)
public abstract class IngredientTagSelectionTooltipSelfRenderMixin {

    /** 与 JEIU 内部的 PADDING 保持一致（私有常量，这里就地复制）。 */
    private static final int TENSHI_PADDING = 4;
    /** 原版风格深色 tooltip 背景（用户确认可用）。 */
    private static final int TENSHI_BACKGROUND = 0xF0100010;
    /** 兜底：JEIU 的屏幕变暗色（取不到时使用）。 */
    private static final int TENSHI_FALLBACK_DIM = 0x66000000;

    /** 一次性日志标记：确认自绘路径确实生效。 */
    private static boolean tenshisJei$loggedSelfRender;

    @Shadow(remap = false)
    private ImmutableRect2i area;

    @Inject(method = "draw(Lnet/minecraft/client/gui/GuiGraphics;II)V", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void tenshisJei$selfRenderTagPanel(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (!isTooltipOverhaulLoaded()) {
            return; // 未装 tooltip-overhaul：完全保持 JEIU 原行为
        }
        ImmutableRect2i rect = this.area;
        if (rect == null || rect.width() <= 0 || rect.height() <= 0) {
            return; // 面板未显示（已 hide）
        }
        try {
            Minecraft minecraft = Minecraft.getInstance();
            int left = rect.x();
            int top = rect.y();
            int right = left + rect.width();
            int bottom = top + rect.height();

            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, mezz.jei.common.gui.GuiRenderLayers.TOOLTIP_Z);
            // 1) 屏幕变暗（与 JEIU 原逻辑一致）
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), dimColor());
            // 2) 自己画深色背景（原来由 tooltip 管线画，现在管线被 tooltip-overhaul 接管了）
            graphics.fill(left, top, right, bottom, TENSHI_BACKGROUND);
            // 3) 复用 JEIU 自己的绘制：白边框 + 标题 + 标签行 + 悬停高亮 + 滚动条
            //    注意传入的 x/y 与命中判定 getTagIndex() 使用的是同一个 area，故必然对齐。
            ((ClientTooltipComponent) (Object) this)
                .renderImage(minecraft.font, left + TENSHI_PADDING, top + TENSHI_PADDING, graphics);
            graphics.pose().popPose();
            if (!tenshisJei$loggedSelfRender) {
                tenshisJei$loggedSelfRender = true;
                EtLog.info("[ET-jei] 标签选择面板已改用自绘（检测到 tooltip-overhaul，绘制矩形与命中矩形一致）rect=({},{}) {}x{}",
                    left, top, rect.width(), rect.height());
            }
            ci.cancel();
        } catch (Throwable t) {
            EtLog.info("[ET-jei] 标签面板自绘失败，回退 JEIU 原渲染: " + t);
        }
    }

    private static boolean isTooltipOverhaulLoaded() {
        try {
            return net.neoforged.fml.ModList.get().isLoaded("tooltipoverhaul");
        } catch (Throwable t) {
            return false;
        }
    }

    private static int dimColor() {
        try {
            return mezz.jei.common.gui.JeiGuiColors.getColor(
                mezz.jei.common.gui.JeiGuiColors.GuiColor.INTERACTIVE_INGREDIENT_TOOLTIP_SCREEN_DIM);
        } catch (Throwable t) {
            return TENSHI_FALLBACK_DIM;
        }
    }
}
