package com.busyorc.tenshis_jei.mixin;

import com.busyorc.tenshis_jei.compat.et.EtLog;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.common.bookmarks.CraftingStackMatcher;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkCraftingGridFill;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingBridge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 诊断用 mixin（常开日志，require=0）：复刻 fork 发包前的判定链并输出结果，
 * 用来定位"配方树里的配方为什么没发包"：
 *   1) 布局能否解析（recipeLayoutResolver）；
 *   2) BookmarkCraftingGridFill.create 是否成功（targetSlotCount 必须是 4/9 等）；
 *   3) canCraft 是否通过（逐项列出缺多少材料）。
 * 纯只读计算，不改变任何行为。
 */
@Mixin(value = BookmarkAutoCraftingBridge.class, remap = false)
public abstract class BookmarkAutoCraftingCraftDiagnosticsMixin {

    @Inject(
        method = "craft(Lnet/minecraft/resources/ResourceLocation;IIILjava/util/function/Supplier;Ljava/util/function/Function;Ljava/util/function/Consumer;ZIILjava/util/concurrent/atomic/AtomicBoolean;Ljava/lang/Runnable;)Z",
        at = @At("HEAD"),
        require = 0,
        remap = false
    )
    private static void tenshisJei$diagCraft(
        ResourceLocation recipeUid,
        int multiplier,
        int targetSlotCount,
        int containerId,
        Supplier<List<ItemStack>> availableStacksSupplier,
        Function<ResourceLocation, Optional<IRecipeLayoutDrawable<?>>> recipeLayoutResolver,
        Consumer<?> packetSender,
        boolean simulate,
        int taskId,
        int requestId,
        AtomicBoolean craftedOnePacket,
        Runnable afterCraftAccepted,
        CallbackInfoReturnable<Boolean> cir
    ) {
        try {
            Optional<IRecipeLayoutDrawable<?>> layout = recipeLayoutResolver.apply(recipeUid);
            List<ItemStack> available = availableStacksSupplier.get();
            EtLog.info("[ET-jei] diag: recipe={} mult={} targetSlots={} layout={} available={} simulate={}",
                recipeUid, multiplier, targetSlotCount, layout.isPresent(), available.size(), simulate);
            if (layout.isEmpty()) {
                EtLog.info("[ET-jei] diag: -> 布局解析失败（书签/类别问题），fork 不会发包");
                return;
            }
            Optional<BookmarkCraftingGridFill> fill =
                BookmarkCraftingGridFill.create(layout.get(), targetSlotCount, multiplier, available);
            if (fill.isEmpty()) {
                EtLog.info("[ET-jei] diag: -> 填充生成失败（targetSlotCount 或配方槽位不匹配），fork 不会发包");
                return;
            }
            List<ItemStack> targets = fill.get().targetStacks();
            int effMult = fill.get().multiplier();
            EtLog.info("[ET-jei] diag: fill ok, effMult={} targets={}", effMult, describe(targets));
            List<ItemStack> remaining = new ArrayList<>();
            for (ItemStack stack : available) {
                if (!stack.isEmpty()) {
                    remaining.add(stack.copy());
                }
            }
            boolean ok = true;
            for (ItemStack target : targets) {
                if (target.isEmpty()) {
                    continue;
                }
                int required = target.getCount() * Math.max(1, effMult);
                for (ItemStack stock : remaining) {
                    if (required <= 0) {
                        break;
                    }
                    if (stock.isEmpty() || !CraftingStackMatcher.matchesIngredientTemplate(target, stock)) {
                        continue;
                    }
                    int consumed = Math.min(required, stock.getCount());
                    stock.shrink(consumed);
                    required -= consumed;
                }
                if (required > 0) {
                    ok = false;
                    EtLog.info("[ET-jei] diag: 材料不足 {} x{} (还需 {})", target.getItem(), target.getCount(), required);
                }
            }
            EtLog.info("[ET-jei] diag: -> canCraft={} {}", ok, ok ? "(应会发包)" : "(fork 不会发包)");
        } catch (Throwable t) {
            EtLog.info("[ET-jei] diag threw: " + t);
        }
    }

    /** 记录 craft() 的真实返回值：true=已调用 packetSender（包已发出），false=fork 拒绝。 */
    @Inject(
        method = "craft(Lnet/minecraft/resources/ResourceLocation;IIILjava/util/function/Supplier;Ljava/util/function/Function;Ljava/util/function/Consumer;ZIILjava/util/concurrent/atomic/AtomicBoolean;Ljava/lang/Runnable;)Z",
        at = @At("RETURN"),
        require = 0,
        remap = false
    )
    private static void tenshisJei$diagCraftResult(
        ResourceLocation recipeUid,
        int multiplier,
        int targetSlotCount,
        int containerId,
        Supplier<List<ItemStack>> availableStacksSupplier,
        Function<ResourceLocation, Optional<IRecipeLayoutDrawable<?>>> recipeLayoutResolver,
        Consumer<?> packetSender,
        boolean simulate,
        int taskId,
        int requestId,
        AtomicBoolean craftedOnePacket,
        Runnable afterCraftAccepted,
        CallbackInfoReturnable<Boolean> cir
    ) {
        EtLog.info("[ET-jei] diag: craft() returned={} recipe={} simulate={} craftedOnePacket={} (taskId={} requestId={})",
            Boolean.valueOf(cir.getReturnValue()), recipeUid, simulate, craftedOnePacket.get(), taskId, requestId);
    }

    private static String describe(List<ItemStack> stacks) {
        StringBuilder sb = new StringBuilder("[");
        int count = 0;
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            if (count++ > 0) {
                sb.append(", ");
            }
            sb.append(stack.getCount()).append("x ").append(stack.getItem());
        }
        return sb.append(']').toString();
    }
}
