package com.busyorc.tenshis_jei.mixin;

import com.busyorc.tenshis_jei.network.WirelessSnapshotDataPayload;
import com.busyorc.tenshis_jei.network.WirelessPayloadRegistrar;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.chain.BookmarkPullPlanner;
import mezz.jei.gui.bookmarks.chain.RecipeChainDetails;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainMath;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 拉取规划完成时（客户端），对比配方链完整需求与网络快照，
 * 把缺口(required - available)通过 CraftRequestPayload 发给服务端触发 AE2 自动合成。
 */
@Mixin(value = BookmarkPullPlanner.class, remap = false)
public abstract class BookmarkPullPlannerAutoCraftMixin {

    @Inject(method = "plan", at = @At("RETURN"), remap = false)
    private static void tenshisJei$captureShortfall(
        List<RecipeChainInput> inputs,
        Set<ResourceLocation> collapsedRecipes,
        Map<BookmarkIngredientKey, Long> playerInventory,
        Map<BookmarkIngredientKey, Long> containerStorage,
        int freeSlots,
        int maxStackSize,
        boolean shift,
        CallbackInfoReturnable<BookmarkPullPlanner.BookmarkPullPlan> cir
    ) {
        try {
            BookmarkPullPlanner.BookmarkPullPlan plan = cir.getReturnValue();
            if (plan == null || plan.amounts().isEmpty()) {
                return;
            }
            RecipeChainDetails details = RecipeChainMath.refresh(inputs, collapsedRecipes);
            List<WirelessSnapshotDataPayload.Entry> shortfalls = new ArrayList<>();
            for (Map.Entry<BookmarkIngredientKey, Long> entry : details.missedItems().entrySet()) {
                long required = entry.getValue();
                long available = findAvailable(containerStorage, entry.getKey());
                long shortfall = required - available;
                if (shortfall <= 0) {
                    continue;
                }
                ItemStack stack = itemStackFromKey(entry.getKey());
                if (!stack.isEmpty()) {
                    shortfalls.add(new WirelessSnapshotDataPayload.Entry(stack, shortfall));
                }
            }
            if (!shortfalls.isEmpty()) {
                com.busyorc.tenshis_jei.TenshisJeiLog.info("[ET-jei] auto-craft request: " + shortfalls.size() + " item(s)");
                WirelessPayloadRegistrar.sendCraftRequest(shortfalls);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 用 fork 的宽松匹配(matchesCraftingAvailable)在快照里找可用量，容忍 NBT/count 差异。 */
    private static long findAvailable(Map<BookmarkIngredientKey, Long> containerStorage, BookmarkIngredientKey key) {
        for (Map.Entry<BookmarkIngredientKey, Long> entry : containerStorage.entrySet()) {
            if (entry.getKey().matchesCraftingAvailable(key)) {
                return entry.getValue();
            }
        }
        return 0L;
    }

    /** 从 BookmarkIngredientKey 重建代表 ItemStack（优先 SNBT，回退到 ingredientUid 的 item id）。 */
    private static ItemStack itemStackFromKey(BookmarkIngredientKey key) {
        if (key.serializedIngredient() != null) {
            try {
                CompoundTag tag = NbtUtils.snbtToStructure(key.serializedIngredient());
                net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
                if (tag != null && minecraft.level != null) {
                    ItemStack stack = ItemStack.parseOptional(minecraft.level.registryAccess(), tag);
                    if (!stack.isEmpty()) {
                        return stack;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        String uid = key.ingredientUid();
        if (uid != null) {
            String[] segments = uid.split(":");
            if (segments.length >= 2) {
                ResourceLocation rl = ResourceLocation.tryParse(segments[segments.length - 2] + ":" + segments[segments.length - 1]);
                if (rl != null) {
                    Item item = BuiltInRegistries.ITEM.get(rl);
                    if (item != Items.AIR) {
                        return new ItemStack(item);
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }
}
