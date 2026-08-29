package com.busyorc.tenshis_jei.mixin;

import com.busyorc.tenshis_jei.TenshisJeiCraftingModes;
import com.busyorc.tenshis_jei.TenshisJeiLog;
import mezz.jei.gui.bookmarks.chain.AutoCraftingManager;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainMath;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * New logic (EXACT, default): when the recipe-tree auto-crafting runs, feed an
 * EMPTY inventory snapshot to the chain math so it dispatches exactly the
 * quantity set in the tree (nothing is subtracted for items already carried).
 * Legacy mode leaves the original inventory snapshot untouched.
 */
@Mixin(BookmarkAutoCraftingBridge.class)
public class BookmarkAutoCraftingBridgeExactCraftingMixin {

    @Redirect(
            method = "activate",
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/gui/bookmarks/chain/AutoCraftingManager;run(Lmezz/jei/gui/bookmarks/chain/RecipeChainMath;Ljava/util/List;Ljava/util/function/Supplier;Lmezz/jei/gui/bookmarks/chain/AutoCraftingManager$RecipeExecutor;Ljava/util/function/BooleanSupplier;)Lmezz/jei/gui/bookmarks/chain/AutoCraftingManager$Result;"
            ),
            require = 0
    )
    private static AutoCraftingManager.Result tenshisJei$runExact(
            RecipeChainMath math,
            List<RecipeChainInput> initialItems,
            Supplier<List<RecipeChainInput>> inventory,
            AutoCraftingManager.RecipeExecutor executor,
            BooleanSupplier interrupted
    ) {
        if (!TenshisJeiCraftingModes.isExactTreeQuantityEnabled()) {
            return AutoCraftingManager.run(math, initialItems, inventory, executor, interrupted);
        }
        TenshisJeiLog.info("[ET-jei] tree craft EXACT (one-shot): empties inventory snapshot");
        return AutoCraftingManager.run(math, initialItems, List::of, executor, interrupted);
    }
}
