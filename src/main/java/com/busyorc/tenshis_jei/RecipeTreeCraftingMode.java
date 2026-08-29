package com.busyorc.tenshis_jei;

/**
 * Recipe-tree (JEIU 配方树) auto-crafting quantity mode, switchable via config.
 * <p>
 * EXACT (default, new logic): shift+C always crafts exactly the quantity set
 * in the recipe tree, no matter how many result items are already carried.
 * <p>
 * LEGACY (old logic): keep JEIU's built-in behavior — the crafted quantity is
 * the tree quantity adjusted by the player inventory (top-up logic).
 */
public enum RecipeTreeCraftingMode {
    EXACT,
    LEGACY
}
