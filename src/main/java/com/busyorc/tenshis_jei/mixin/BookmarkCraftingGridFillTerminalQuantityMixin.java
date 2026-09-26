package com.busyorc.tenshis_jei.mixin;

import com.busyorc.tenshis_jei.TenshisJeiCraftingModes;
import com.busyorc.tenshis_jei.compat.et.EtLog;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.common.bookmarks.CraftingStackMatcher;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkCraftingGridFill;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 修正"本模组终端（ET / WCWT）里配方树合成的数量"。
 * <p>
 * JEIU 的 {@code BookmarkCraftingGridFill.calculateRecipeQuantity} 把数量按
 * {@code 分布槽数 × 该物品最大堆叠} 封顶。锻造台材料都是不可堆叠物品
 * （下界合金模板 / 护腿 / 锭各自占 1 格、堆叠 1）→ 容量恒为 1 → 每轮只发 1 个，
 * 于是"设定 4 个只合成 1 个、设定 6/7 个甚至 0 个"。本模组的终端在服务端是
 * **逐份填料合成**（不存在格子上限），所以这里改成按"材料实际可用量"计算上限。
 * <p>
 * 仅在 {@link TenshisJeiCraftingModes#isTerminalFillWindow()} 为 true（即本模组终端
 * 正在派发配方树）时生效，其它情况完全保持 JEIU 原行为。
 */
@Mixin(value = BookmarkCraftingGridFill.class, remap = false)
public abstract class BookmarkCraftingGridFillTerminalQuantityMixin {

    @Inject(
        method = "create(Lmezz/jei/api/gui/IRecipeLayoutDrawable;IILjava/util/List;)Ljava/util/Optional;",
        at = @At("RETURN"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private static void tenshisJei$terminalQuantity(
        IRecipeLayoutDrawable<?> recipeLayout,
        int targetSlotCount,
        int multiplier,
        List<ItemStack> availableStacks,
        CallbackInfoReturnable<Optional<BookmarkCraftingGridFill>> cir
    ) {
        try {
            if (!TenshisJeiCraftingModes.isTerminalFillWindow()) {
                return;
            }
            Optional<BookmarkCraftingGridFill> fill = cir.getReturnValue();
            if (fill == null || fill.isEmpty()) {
                return;
            }
            int requested = multiplier <= 0 ? 64 : multiplier;
            int current = fill.get().multiplier();
            if (current >= requested) {
                return;
            }
            int byMaterials = materialLimitedMultiplier(fill.get().targetStacks(), availableStacks);
            int fixed = Math.max(1, Math.min(requested, byMaterials));
            if (fixed > current) {
                EtLog.info("[ET-jei] 终端数量修正: 请求 {} -> JEIU 封顶 {} -> 按材料可用改为 {}",
                    requested, current, fixed);
                cir.setReturnValue(Optional.of(new BookmarkCraftingGridFill(fill.get().targetStacks(), fixed)));
            }
        } catch (Throwable t) {
            EtLog.info("[ET-jei] 终端数量修正异常（保持 JEIU 原值）: " + t);
        }
    }

    /** 按"每份配方材料 × N"计算材料允许的最大份数（只按材料，不考虑槽位容量）。 */
    private static int materialLimitedMultiplier(List<ItemStack> targets, List<ItemStack> availableStacks) {
        if (targets == null || targets.isEmpty() || availableStacks == null) {
            return 1;
        }
        List<ItemStack> pool = new ArrayList<>();
        for (ItemStack stack : availableStacks) {
            if (!stack.isEmpty()) {
                pool.add(stack.copy());
            }
        }
        int limit = Integer.MAX_VALUE;
        for (ItemStack target : targets) {
            if (target.isEmpty() || target.getCount() <= 0) {
                continue;
            }
            int perCraft = target.getCount();
            int matched = 0;
            for (ItemStack stock : pool) {
                if (stock.isEmpty() || !CraftingStackMatcher.matchesIngredientTemplate(target, stock)) {
                    continue;
                }
                matched += stock.getCount();
            }
            limit = Math.min(limit, matched / perCraft);
        }
        return limit == Integer.MAX_VALUE ? 1 : Math.max(1, limit);
    }
}
