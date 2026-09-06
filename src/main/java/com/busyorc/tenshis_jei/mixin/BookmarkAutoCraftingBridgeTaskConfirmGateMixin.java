package com.busyorc.tenshis_jei.mixin;

import com.busyorc.tenshis_jei.compat.tinkers.AnvilAutoReSlot;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 砧自动强化配套：在 fork 的 BookmarkAutoCraftingBridge.Task 上，把"配方步完成的库存确认"
 * 在本模组砧会话激活期间，从"等物品栏净增"改写为"等工具真正回到 slot0"。
 *
 * 原因：工匠砧加 modifier 的结果是被取走的工具，不会造成物品栏净增，fork 默认确认逻辑对该场景
 * 每步都会硬等 MAX_WAIT_TICKS(60=3s) 超时，且会在工具回槽前就 dispatch 下一个 modifier 导致
 * 竞态（slot0 空则下一强化无法执行）。本门控让 fork 等到 AnvilAutoReSlot 把工具放回 slot0 才
 * 继续，从而无竞态、无 3 秒干等地连续施加多个 modifier。
 */
@Mixin(value = BookmarkAutoCraftingBridge.Task.class, remap = false)
public abstract class BookmarkAutoCraftingBridgeTaskConfirmGateMixin {

    @Shadow(remap = false)
    protected abstract boolean inventoryHasExpectedResultIncreaseSinceDispatch();

    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lmezz/jei/gui/bookmarks/hotkeys/BookmarkAutoCraftingBridge$Task;inventoryHasExpectedResultIncreaseSinceDispatch()Z"
        )
    )
    private boolean tenshisJei$anvilConfirmGate(BookmarkAutoCraftingBridge.Task self) {
        if (AnvilAutoReSlot.isActive()) {
            if (AnvilAutoReSlot.toolBackInSlot0()) {
                return true; // 工具已回 slot0 -> 该步"确认"，dispatch 下一个
            }
            if (AnvilAutoReSlot.emptyForTooLong()) {
                return this.inventoryHasExpectedResultIncreaseSinceDispatch(); // 异常兜底：走原逻辑
            }
            return false; // 工具还没回槽：继续等，不超时不干等
        }
        return this.inventoryHasExpectedResultIncreaseSinceDispatch();
    }
}
