package com.busyorc.tenshis_jei.mixin;

import com.busyorc.tenshis_jei.TenshisJeiLog;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把收藏面板底部的"步数量输入栏位"（ScrollStepTextField 的布局区域）右移 20px。
 * 注入点：BookmarkOverlay.calculateScrollStepArea（public static，返回栏位矩形）。
 * 复刻 fork 原公式（历史按钮右缘 + INNER_PADDING=2），再加 SHIFT_PIXELS=20；
 * 右边界（rightBoundary）保持锚定，因此栏位宽度相应减少 20px。
 * ScrollStepTextField 的文本框/背景绘制与鼠标命中全部基于该矩形，随注入结果自动右移。
 */
@Mixin(value = BookmarkOverlay.class, remap = false)
public abstract class BookmarkOverlayScrollStepAreaShiftMixin {

    /** fork 常量：INNER_PADDING = 2（BookmarkOverlay 静态私有字段，直接内联避免 @Shadow 私有静态字段） */
    private static final int INNER_PADDING = 2;
    private static final int SHIFT_PIXELS = 20;

    @Inject(method = "calculateScrollStepArea", at = @At("HEAD"), cancellable = true, remap = false)
    private static void tenshisJei$shiftScrollStepArea(
        ImmutableRect2i historyButtonArea,
        int rightBoundary,
        CallbackInfoReturnable<ImmutableRect2i> cir
    ) {
        int x = historyButtonArea.getX() + historyButtonArea.getWidth() + INNER_PADDING + SHIFT_PIXELS;
        int width = rightBoundary - x + 1;
        cir.setReturnValue(new ImmutableRect2i(x, historyButtonArea.getY(), Math.max(0, width), historyButtonArea.getHeight()));
        TenshisJeiLog.info("[ET-jei] scroll step area shifted right by " + SHIFT_PIXELS + " px -> x=" + x + ", w=" + Math.max(0, width));
    }
}
