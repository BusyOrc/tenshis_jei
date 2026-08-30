package com.busyorc.tenshis_jei;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for Tenshi's JEI Addon; saved as config/tenshis_jei_addon.toml.
 */
public final class TenshisJeiConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue DEBUG = BUILDER
            .comment(
                    "Enable debug logging. When false (default), the mod writes no log lines at all.",
                    "启用调试日志。默认 false：关闭时不输出任何日志，避免污染日志。"
            )
            .define("debug", false);

    public static final ModConfigSpec.EnumValue<RecipeTreeCraftingMode> RECIPE_TREE_CRAFTING_MODE = BUILDER
            .comment(
                    "Recipe-tree craft quantity mode (affects JEIU shift+C auto-crafting).",
                    "EXACT (default, new logic): always craft exactly the quantity set in the recipe tree,",
                    "regardless of how many result items are already in the inventory.",
                    "LEGACY (old logic): keep JEIU's built-in inventory-aware top-up behavior.",
                    "配方树合成数量模式：EXACT=精确按树设定数量合成（默认，新逻辑）；LEGACY=维持 JEIU 原有逻辑（旧逻辑）。"
            )
            .defineEnum("recipeTreeCraftingMode", RecipeTreeCraftingMode.EXACT);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private TenshisJeiConfig() {
    }
}
