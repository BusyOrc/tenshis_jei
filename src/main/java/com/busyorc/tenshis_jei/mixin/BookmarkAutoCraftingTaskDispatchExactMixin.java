package com.busyorc.tenshis_jei.mixin;

import com.busyorc.tenshis_jei.TenshisJeiCraftingModes;
import com.busyorc.tenshis_jei.TenshisJeiLog;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.chain.AutoCraftingManager;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainMath;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingBridge;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * New logic (EXACT, default). On the FIRST dispatch after the tree is activated:
 * - the inventory snapshot is emptied so every recipe counts as missing;
 * - final products whose inputs are produced by recipes IN this tree are deferred
 *   (return false: skipped without counting as crafted, the run keeps processing
 *   the other recipes, and the task's next dispatch crafts the final product once
 *   its inputs are available);
 * - input/producer recipes are forced to at least the amount the final product
 *   needs (their own tree-set is the minimum), so the tree produces enough.
 * On SUBSEQUENT dispatches the real inventory is used and the chain math runs
 * naturally, so the task terminates once the tree-set quantities are reached.
 * Legacy mode untouched.
 */
@Mixin(BookmarkAutoCraftingBridge.Task.class)
public class BookmarkAutoCraftingTaskDispatchExactMixin {

    @Shadow
    private List<RecipeChainInput> chainInputs;

    @Unique
    private boolean tenshisJei$firstDispatchDone;

    @Inject(method = "dispatchNext", at = @At("HEAD"), require = 0)
    private void tenshisJei$logTreeInputs(CallbackInfoReturnable<Boolean> ci) {
        TenshisJeiLog.info("[ET-jei] task chain inputs:");
        for (RecipeChainInput input : chainInputs) {
            TenshisJeiLog.info("[ET-jei]   input: type=" + input.metadata().type()
                    + " recipe=" + input.metadata().recipeUid()
                    + " mul=" + input.metadata().multiplier()
                    + " factor=" + input.metadata().factor()
                    + " amount=" + input.metadata().amount());
        }
    }

    @Redirect(
            method = "dispatchNext",
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/gui/bookmarks/chain/AutoCraftingManager;run(Lmezz/jei/gui/bookmarks/chain/RecipeChainMath;Ljava/util/List;Ljava/util/function/Supplier;Lmezz/jei/gui/bookmarks/chain/AutoCraftingManager$RecipeExecutor;Ljava/util/function/BooleanSupplier;)Lmezz/jei/gui/bookmarks/chain/AutoCraftingManager$Result;"
            ),
            require = 0
    )
    private AutoCraftingManager.Result tenshisJei$runExact(
            RecipeChainMath math,
            List<RecipeChainInput> initialItems,
            Supplier<List<RecipeChainInput>> inventory,
            AutoCraftingManager.RecipeExecutor executor,
            BooleanSupplier interrupted
    ) {
        if (!TenshisJeiCraftingModes.isExactTreeQuantityEnabled()) {
            return AutoCraftingManager.run(math, initialItems, inventory, executor, interrupted);
        }
        boolean isFirstDispatch = !tenshisJei$firstDispatchDone;
        Supplier<List<RecipeChainInput>> effectiveInventory = isFirstDispatch ? List::of : inventory;
        AutoCraftingManager.RecipeExecutor effectiveExecutor = executor;
        if (isFirstDispatch) {
            List<RecipeChainInput> ingredientNodes = new ArrayList<>();
            List<RecipeChainInput> resultNodes = new ArrayList<>();
            Set<ResourceLocation> deferRecipes = new HashSet<>();
            Map<ResourceLocation, Long> treeSet = new HashMap<>();
            Map<ResourceLocation, Long> demandedAmount = new HashMap<>();
            for (RecipeChainInput input : chainInputs) {
                BookmarkItemMetadata md = input.metadata();
                ResourceLocation uid = md.recipeUid();
                if (uid == null) {
                    continue;
                }
                if (md.type().isGraphOutput()) {
                    resultNodes.add(input);
                    treeSet.merge(uid, md.multiplier(), Math::max);
                } else if (md.type().isGraphInput()) {
                    ingredientNodes.add(input);
                }
            }
            for (RecipeChainInput ingredient : ingredientNodes) {
                ResourceLocation consumerRecipe = ingredient.metadata().recipeUid();
                long consumeAmount = ingredient.metadata().amount();
                boolean hasTreeProducer = false;
                for (RecipeChainInput result : resultNodes) {
                    ResourceLocation producerRecipe = result.metadata().recipeUid();
                    if (producerRecipe == null || producerRecipe.equals(consumerRecipe)) {
                        continue;
                    }
                    if (ingredient.metadata().isSatisfiedBy(result.metadata())) {
                        demandedAmount.merge(producerRecipe, consumeAmount, Math::max);
                        hasTreeProducer = true;
                    }
                }
                if (hasTreeProducer) {
                    deferRecipes.add(consumerRecipe);
                }
            }
            for (Map.Entry<ResourceLocation, Long> entry : demandedAmount.entrySet()) {
                long demand = entry.getValue();
                for (RecipeChainInput result : resultNodes) {
                    if (entry.getKey().equals(result.metadata().recipeUid())) {
                        treeSet.merge(entry.getKey(), result.metadata().multiplierFromAmount(demand), Math::max);
                        break;
                    }
                }
            }
            effectiveExecutor = (recipeUid, multiplier) -> {
                if (deferRecipes.contains(recipeUid)) {
                    TenshisJeiLog.info("[ET-jei] EXACT defer top -> " + recipeUid);
                    return false;
                }
                long forced = treeSet.getOrDefault(recipeUid, -1L);
                int target = (forced > 0 && forced <= Integer.MAX_VALUE) ? (int) forced : multiplier;
                TenshisJeiLog.info("[ET-jei] EXACT dispatch -> " + recipeUid + " x" + multiplier + " (tree-set " + target + ")");
                return executor.craft(recipeUid, target);
            };
        }
        AutoCraftingManager.Result result = AutoCraftingManager.run(math, initialItems, effectiveInventory, effectiveExecutor, interrupted);
        tenshisJei$firstDispatchDone = true;
        return result;
    }
}