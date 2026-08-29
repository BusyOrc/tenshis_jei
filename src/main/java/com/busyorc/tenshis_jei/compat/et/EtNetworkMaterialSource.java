package com.busyorc.tenshis_jei.compat.et;

import com.busyorc.tenshis_jei.TenshisJeiLog;
import mezz.jei.common.bookmarks.ServerBookmarkCraftingGridFill;
import me.myogoo.extendedterminal.menu.extendedterminal.ETTerminalMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.items.storage.ViewCellItem;
import appeng.util.prioritylist.IPartitionList;

/**
 * Extracts crafting ingredients from an Extended Terminal's linked network storage.
 * Mirrors the JEI fork's Ae2NetworkMaterialSource but uses AE2's canonical powered
 * extraction (StorageHelper.poweredExtraction) exactly like ET's own recipe-fill
 * packet does, so linked ET terminals pull network materials just like AE2 terminals.
 */
class EtNetworkMaterialSource implements ServerBookmarkCraftingGridFill.ExternalIngredientSource {
    private final ETTerminalMenu menu;
    private final MEStorage storage;
    private final IEnergySource energySource;
    private final IActionSource actionSource;
    private final @Nullable IPartitionList viewCellFilter;
    private final Level level;
    private final List<ItemStack> intendedItems;
    private @Nullable KeyCounter snapshot;
    private @Nullable RecipeHolder<CraftingRecipe> recipe;
    private @Nullable ItemStack output;

    EtNetworkMaterialSource(ETTerminalMenu menu, ServerPlayer player, List<ItemStack> targetStacks) {
        this.menu = menu;
        this.storage = menu.getHost().getInventory();
        this.energySource = menu.getEnergySource();
        this.actionSource = menu.getActionSource();
        this.viewCellFilter = ViewCellItem.createItemFilter(menu.getViewCells());
        this.level = player.level();
        this.intendedItems = targetStacks.stream().map(ItemStack::copy).toList();
    }

    @Override
    public ItemStack extract(int slotIndex, ItemStack template, int amount) {
        if (!menu.getLinkStatus().connected() || amount <= 0) {
            if (amount > 0) {
                TenshisJeiLog.info("[ET-jei] extract blocked: link disconnected");
            }
            return ItemStack.EMPTY;
        }
        ItemStack exact = extractExact(AEItemKey.of(template), amount);
        if (!exact.isEmpty()) {
            return exact;
        }
        return extractFuzzy(slotIndex, template, amount);
    }

    @Override
    public long countAvailable(int slotIndex, ItemStack template) {
        if (!menu.getLinkStatus().connected()) {
            return 0;
        }
        AEItemKey exactKey = AEItemKey.of(template);
        if (isListed(exactKey)) {
            long exact = getSnapshot().get(exactKey);
            if (exact > 0) {
                return exact;
            }
        }
        if (template.getComponents().isEmpty() && !template.isDamageableItem()) {
            return 0;
        }
        AEItemKey candidate = findFuzzyCandidate(slotIndex, template);
        long total = candidate == null ? 0 : getSnapshot().get(candidate);
        TenshisJeiLog.info("[ET-jei] countAvailable slot{} {} => {} (snapshot keys {})", slotIndex, template, total, getSnapshot().size());
        return total;
    }

    private ItemStack extractExact(AEItemKey key, int amount) {
        if (!isListed(key)) {
            return ItemStack.EMPTY;
        }
        long extracted = StorageHelper.poweredExtraction(energySource, storage, key, amount, actionSource);
        if (extracted <= 0) {
            TenshisJeiLog.info("[ET-jei] extractExact miss: {} x{} (snapshot had {})", key, amount, getSnapshot().get(key));
            return ItemStack.EMPTY;
        }
        TenshisJeiLog.info("[ET-jei] extractExact ok: {} x{}", key, extracted);
        return key.toStack((int) extracted);
    }

    private ItemStack extractFuzzy(int slotIndex, ItemStack template, int amount) {
        AEItemKey candidate = findFuzzyCandidate(slotIndex, template);
        if (candidate == null) {
            return ItemStack.EMPTY;
        }
        return extractExact(candidate, amount);
    }

    private @Nullable AEItemKey findFuzzyCandidate(int slotIndex, ItemStack template) {
        RecipeHolder<CraftingRecipe> recipe = getRecipe();
        if (recipe == null || output == null) {
            return null;
        }
        for (var entry : getSnapshot()) {
            if (!(entry.getKey() instanceof AEItemKey itemKey)) {
                continue;
            }
            if (itemKey.getItem() != template.getItem() || itemKey.matches(output) || !isListed(itemKey)) {
                continue;
            }
            List<ItemStack> adjustedItems = new ArrayList<>(intendedItems);
            adjustedItems.set(slotIndex, itemKey.toStack(Math.max(1, intendedItems.get(slotIndex).getCount())));
            CraftingInput adjustedInput = CraftingInput.of(3, 3, adjustedItems);
            if (!recipe.value().matches(adjustedInput, level)) {
                continue;
            }
            if (!ItemStack.matches(recipe.value().assemble(adjustedInput, level.registryAccess()), output)) {
                continue;
            }
            return itemKey;
        }
        return null;
    }

    private @Nullable RecipeHolder<CraftingRecipe> getRecipe() {
        if (recipe == null) {
            CraftingInput intendedInput = CraftingInput.of(3, 3, intendedItems);
            recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, intendedInput, level).orElse(null);
            if (recipe != null) {
                output = recipe.value().assemble(intendedInput, level.registryAccess());
            }
        }
        return recipe;
    }

    private KeyCounter getSnapshot() {
        if (snapshot == null) {
            snapshot = storage.getAvailableStacks();
            TenshisJeiLog.info("[ET-jei] first snapshot: {} keys", snapshot.size());
        }
        return snapshot;
    }

    private boolean isListed(AEItemKey key) {
        return viewCellFilter == null || viewCellFilter.isEmpty() || viewCellFilter.isListed(key);
    }
}
