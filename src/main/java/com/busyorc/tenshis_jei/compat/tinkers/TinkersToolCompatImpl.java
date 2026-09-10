package com.busyorc.tenshis_jei.compat.tinkers;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.Internal;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.RecipeLayoutProjection;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.recipe.TinkerRecipeTypes;
import slimeknights.tconstruct.library.recipe.modifiers.adding.IDisplayModifierRecipe;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationRecipe;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 匠魂运行时实现（仅在 tconstruct 加载时由桥接类加载）。
 * 额外 modifier = ToolStack.getUpgrades()（台/砧应用，与材料自带分开存储）；
 * modifier 配方 = TINKER_STATION 配方里匹配 ModifierId 的 IDisplayModifierRecipe，原材料 = getDisplayItems()。
 * <p>
 * 建树规则（按用户需求）：
 * - 一个 modifier 配方建「一组」，该配方的所有原材料放在同一组里；
 * - 组内容只有该「匠魂工作站」配方图块本身（原材料的输入槽都在图块里），
 *   不再链式加入原材料的工作台合成/砂铸等配方（JEI tag 类别也忽略），
 *   即"只保留 modifier 配方的原材料本身"。
 */
final class TinkersToolCompatImpl {
    private TinkersToolCompatImpl() {
    }

    static boolean isTinkersTool(ItemStack stack) {
        return stack.is(TinkerTags.Items.MODIFIABLE);
    }

    static List<String> buildModifierRecipeTrees(ItemStack toolStack, BookmarkList bookmarkList, IIngredientManager ingredientManager) {
        ToolStack tool = ToolStack.from(toolStack);
        ModifierNBT upgrades = tool.getUpgrades();
        List<ModifierEntry> extras = new ArrayList<>();
        if (upgrades != null) {
            for (ModifierEntry entry : upgrades) {
                extras.add(entry);
            }
        }
        com.busyorc.tenshis_jei.TenshisJeiLog.info("[ET-jei] tinkers: tool=" + toolStack + " upgrades=" + extras.size());
        if (extras.isEmpty()) {
            return List.of();
        }
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return List.of();
        }
        RecipeManager recipeManager = level.getRecipeManager();
        @SuppressWarnings({ "rawtypes", "unchecked" })
        net.minecraft.world.item.crafting.RecipeType<ITinkerStationRecipe> stationType = TinkerRecipeTypes.TINKER_STATION.get();
        List<RecipeHolder<ITinkerStationRecipe>> stationRecipes = recipeManager.getAllRecipesFor(stationType);
        com.busyorc.tenshis_jei.TenshisJeiLog.info("[ET-jei] tinkers: extra modifier count = " + extras.size() + ", tinker-station recipes = " + stationRecipes.size());

        List<String> groupIds = new ArrayList<>();
        for (ModifierEntry entry : extras) {
            ModifierId modifierId = entry.getId();
            List<IDisplayModifierRecipe> modifierRecipes = new ArrayList<>();
            for (RecipeHolder<ITinkerStationRecipe> holder : stationRecipes) {
                if (holder.value() instanceof IDisplayModifierRecipe display
                    && display.getModifier() != null
                    && modifierId.equals(display.getModifier().getId())) {
                    modifierRecipes.add(display);
                }
            }
            if (modifierRecipes.isEmpty()) {
                com.busyorc.tenshis_jei.TenshisJeiLog.info("[ET-jei] tinkers: modifier " + modifierId + " has no display recipe -> skip");
                continue;
            }
            int built = 0;
            for (IDisplayModifierRecipe display : modifierRecipes) {
                // 逐个配方独立容错：某个配方解析失败不能中断其余强化链的生成。
                try {
                    Optional<String> groupId = buildModifierRecipeTree(display, modifierId, bookmarkList, ingredientManager);
                    if (groupId.isPresent()) {
                        groupIds.add(groupId.get());
                        built++;
                    }
                } catch (Throwable t) {
                    com.busyorc.tenshis_jei.TenshisJeiLog.warn("[ET-jei] tinkers: modifier " + modifierId + " group build failed: " + t);
                }
            }
            com.busyorc.tenshis_jei.TenshisJeiLog.info("[ET-jei] tinkers: modifier " + modifierId + " built " + built + " group(s)");
        }
        return groupIds;
    }

    /**
     * 一个 modifier 配方 -> 一组：组内容 = 该 recipes 的匠魂工作站配方图块（所有原材料
     * 都在图块的输入槽里 => "同一配方的原材料放在一起"）。不链原材料的工作台合成/砂铸等配方。
     */
    private static Optional<String> buildModifierRecipeTree(
        IDisplayModifierRecipe display,
        ModifierId modifierId,
        BookmarkList bookmarkList,
        IIngredientManager ingredientManager
    ) {
        List<ItemStack> materials = new ArrayList<>();
        for (int i = 0; i < display.getInputCount(); i++) {
            for (ItemStack stack : display.getDisplayItems(i)) {
                if (!stack.isEmpty()) {
                    materials.add(stack);
                }
            }
        }
        com.busyorc.tenshis_jei.TenshisJeiLog.info("[ET-jei] tinkers: modifier " + modifierId + " has " + materials.size() + " ingredient(s): " + materials);
        if (materials.isEmpty()) {
            return Optional.empty();
        }
        Optional<IRecipeLayoutDrawable<?>> layout = findModifierRecipeLayout(materials.get(0), modifierId);
        com.busyorc.tenshis_jei.TenshisJeiLog.info("[ET-jei] tinkers: modifier layout present = " + layout.isPresent());
        if (layout.isEmpty()) {
            return Optional.empty();
        }
        List<RecipeLayoutProjection> projections = List.of(new RecipeLayoutProjection(layout.get()));
        com.busyorc.tenshis_jei.TenshisJeiLog.info("[ET-jei] tinkers: projections = " + projections.size());
        // 组 id 类型在不同 JEIU 版本间变化过（String -> Integer），这里不依赖其类型，统一转成字符串。
        return bookmarkList.addRecipeLayoutProjectionBookmarkGroup(projections, false).map(String::valueOf);
    }

    /**
     * 找"匠魂 modifiers"类别里该 modifierId 的配方布局。
     * <p>
     * 新的 JEIU 收紧了 focus / tag 匹配，用"原材料 focus"去查类别会让大部分（tag 输入的）
     * modifier 配方查不到，于是只能生成一个强化链。这里改为：直接定位匠魂 modifier 配方类别，
     * 枚举该类别全部配方按 modifierId 匹配，并用【空 focus】创建布局，彻底不依赖 focus 匹配。
     * 旧 focus 方式仅作兜底。
     */
    private static Optional<IRecipeLayoutDrawable<?>> findModifierRecipeLayout(ItemStack material, ModifierId modifierId) {
        IJeiRuntime runtime = Internal.getJeiRuntime();
        if (runtime == null) {
            return Optional.empty();
        }
        IRecipeManager recipeManager = runtime.getRecipeManager();
        IFocusFactory focusFactory = runtime.getJeiHelpers().getFocusFactory();
        IFocusGroup emptyFocusGroup = focusFactory.createFocusGroup(List.of());

        Optional<IRecipeCategory<?>> categoryOpt = resolveModifierCategory(recipeManager);
        if (categoryOpt.isPresent()) {
            IRecipeCategory<?> category = categoryOpt.get();
            try {
                @SuppressWarnings({ "rawtypes", "unchecked" })
                IRecipeCategory rawCategory = (IRecipeCategory) category;
                List<?> recipes = recipeManager.createRecipeLookup(rawCategory.getRecipeType())
                    .includeHidden()
                    .get()
                    .toList();
                com.busyorc.tenshis_jei.TenshisJeiLog.info("[ET-jei] tinkers: modifier category "
                    + category.getRecipeType().getUid() + " has " + recipes.size() + " recipe(s)");
                for (Object recipe : recipes) {
                    IDisplayModifierRecipe display = asDisplayModifierRecipe(recipe);
                    if (display != null
                        && display.getModifier() != null
                        && modifierId.equals(display.getModifier().getId())) {
                        return recipeManager.createRecipeLayoutDrawable(rawCategory, recipe, emptyFocusGroup)
                            .map(l -> (IRecipeLayoutDrawable<?>) l);
                    }
                }
            } catch (Throwable t) {
                com.busyorc.tenshis_jei.TenshisJeiLog.warn("[ET-jei] tinkers: direct modifier lookup failed: " + t);
            }
        }

        return findModifierRecipeLayoutByFocus(material, modifierId, runtime, recipeManager, focusFactory);
    }

    /** 定位匠魂 modifier 配方类别（结果缓存；优先 tconstruct 命名空间）。 */
    private static IRecipeCategory<?> cachedModifierCategory;

    private static Optional<IRecipeCategory<?>> resolveModifierCategory(IRecipeManager recipeManager) {
        IRecipeCategory<?> cached = cachedModifierCategory;
        if (cached != null && categoryHasModifierRecipes(recipeManager, cached)) {
            return Optional.of(cached);
        }
        List<IRecipeCategory<?>> all;
        try {
            all = recipeManager.createRecipeCategoryLookup().get().toList();
        } catch (Throwable t) {
            return Optional.empty();
        }
        for (IRecipeCategory<?> category : all) {
            if (!"tconstruct".equals(category.getRecipeType().getUid().getNamespace())) {
                continue;
            }
            if (categoryHasModifierRecipes(recipeManager, category)) {
                cachedModifierCategory = category;
                return Optional.of(category);
            }
        }
        for (IRecipeCategory<?> category : all) {
            if (category.getRecipeType().getUid().getPath().startsWith("tag_recipes/")) {
                continue;
            }
            if (categoryHasModifierRecipes(recipeManager, category)) {
                cachedModifierCategory = category;
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }

    private static boolean categoryHasModifierRecipes(IRecipeManager recipeManager, IRecipeCategory<?> category) {
        try {
            @SuppressWarnings({ "rawtypes", "unchecked" })
            IRecipeCategory rawCategory = (IRecipeCategory) category;
            return recipeManager.createRecipeLookup(rawCategory.getRecipeType())
                .includeHidden()
                .get()
                .anyMatch(recipe -> asDisplayModifierRecipe(recipe) != null);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 旧方式（focus 匹配）兜底：新 JEIU 下可能匹配不到，仅当直接定位失败时尝试。 */
    private static Optional<IRecipeLayoutDrawable<?>> findModifierRecipeLayoutByFocus(
        ItemStack material,
        ModifierId modifierId,
        IJeiRuntime runtime,
        IRecipeManager recipeManager,
        IFocusFactory focusFactory
    ) {
        Optional<ITypedIngredient<ItemStack>> typedOpt = runtime.getIngredientManager().createTypedIngredient(material);
        if (typedOpt.isEmpty()) {
            return Optional.empty();
        }
        ITypedIngredient<?> typed = typedOpt.get();
        IFocus<?> focus = focusFactory.createFocus(RecipeIngredientRole.INPUT, typed);
        List<IFocus<?>> focuses = List.of(focus);
        IFocusGroup focusGroup = focusFactory.createFocusGroup(focuses);
        try {
            List<IRecipeCategory<?>> categories = recipeManager.createRecipeCategoryLookup()
                .limitFocus(focuses)
                .get()
                .toList();
            for (IRecipeCategory<?> category : categories) {
                if (category.getRecipeType().getUid().getPath().startsWith("tag_recipes/")) {
                    continue;
                }
                @SuppressWarnings({ "rawtypes", "unchecked" })
                IRecipeCategory rawCategory = (IRecipeCategory) (Object) category;
                List<?> recipes = recipeManager.createRecipeLookup(rawCategory.getRecipeType())
                    .limitFocus(focuses)
                    .get()
                    .toList();
                for (Object recipe : recipes) {
                    IDisplayModifierRecipe display = asDisplayModifierRecipe(recipe);
                    if (display != null
                        && display.getModifier() != null
                        && modifierId.equals(display.getModifier().getId())) {
                        return recipeManager.createRecipeLayoutDrawable(rawCategory, recipe, focusGroup)
                            .map(l -> (IRecipeLayoutDrawable<?>) l);
                    }
                }
            }
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    /** 兼容不同 JEIU 版本：配方可能以原始对象或 RecipeHolder 形式返回。 */
    private static IDisplayModifierRecipe asDisplayModifierRecipe(Object recipe) {
        if (recipe instanceof IDisplayModifierRecipe display) {
            return display;
        }
        if (recipe instanceof RecipeHolder<?> holder && holder.value() instanceof IDisplayModifierRecipe display) {
            return display;
        }
        return null;
    }
}
