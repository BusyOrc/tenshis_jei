package com.busyorc.tenshis_jei.mixin;

import com.busyorc.tenshis_jei.TenshisJeiLog;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainMath;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Set;

/**
 * Debug instrumentation: logs the recipe-tree chain inputs when the chain math
 * is built, so we can see the tree-set multiplier/factor per recipe.
 */
@Mixin(RecipeChainMath.class)
public class RecipeChainMathInputLogMixin {

    @Inject(method = "of", at = @At("HEAD"), require = 0)
    private static void tenshisJei$log(List<RecipeChainInput> inputs, Set<ResourceLocation> collapsedRecipes, CallbackInfoReturnable<RecipeChainMath> ci) {
        for (RecipeChainInput input : inputs) {
            TenshisJeiLog.info("[ET-jei] tree input: " + input.metadata().type()
                    + " recipe=" + input.metadata().recipeUid()
                    + " mul=" + input.metadata().multiplier()
                    + " factor=" + input.metadata().factor()
                    + " amount=" + input.metadata().amount());
        }
        TenshisJeiLog.info("[ET-jei] tree collapsed: " + collapsedRecipes);
    }
}