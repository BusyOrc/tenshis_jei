package com.busyorc.tenshis_jei.compat.tinkers;

import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.List;

/**
 * 匠魂可选前置桥接：本类不引用任何 tconstruct 类，
 * 真正的实现（直接引用 tconstruct 类）在 TinkersToolCompatImpl，只有 tconstruct 加载时才会被加载。
 */
public final class TinkersCompatBridge {
    private TinkersCompatBridge() {
    }

    public static boolean isTinkersLoaded() {
        try {
            return ModList.get().isLoaded("tconstruct");
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** shift+F 判定：收藏栏物品是否为匠魂工具（带 tconstruct:modifiable 标签）。 */
    public static boolean isTinkersTool(ItemStack stack) {
        return isTinkersLoaded() && TinkersToolCompatImpl.isTinkersTool(stack);
    }

    /** 为工具的每个额外 modifier（配方非空者）把其原材料建成配方树收藏栏组，返回新建的组 id 列表。 */
    public static List<String> buildModifierRecipeTrees(ItemStack toolStack, BookmarkList bookmarkList, IIngredientManager ingredientManager) {
        if (!isTinkersLoaded()) {
            return List.of();
        }
        return TinkersToolCompatImpl.buildModifierRecipeTrees(toolStack, bookmarkList, ingredientManager);
    }
}