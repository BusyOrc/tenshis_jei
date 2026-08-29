package com.busyorc.tenshis_jei;

/**
 * Reads the recipe-tree crafting mode from the config in a defensive way:
 * if the config is not loaded yet (or failed to load), falls back to EXACT
 * (the new default logic) instead of throwing IllegalStateException.
 */
public final class TenshisJeiCraftingModes {
    private TenshisJeiCraftingModes() {
    }

    public static boolean isExactTreeQuantityEnabled() {
        try {
            return TenshisJeiConfig.RECIPE_TREE_CRAFTING_MODE.get() == RecipeTreeCraftingMode.EXACT;
        } catch (IllegalStateException e) {
            return true;
        }
    }
}
