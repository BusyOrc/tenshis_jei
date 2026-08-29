package com.busyorc.tenshis_jei.compat.et;

import com.busyorc.tenshis_jei.TenshisJeiLog;
import mezz.jei.common.bookmarks.ICraftingGridCraftExecutor;
import mezz.jei.common.bookmarks.ServerBookmarkCraftingGridFill;
import mezz.jei.neoforge.compat.ae2.IJeiCraftingTermSlotExtension;
import me.myogoo.extendedterminal.menu.ETMenuType;
import me.myogoo.extendedterminal.api.adapter.recipe.smithing.ISmithingRecipeAdapter;
import me.myogoo.extendedterminal.menu.extendedterminal.ETTerminalMenu;
import me.myogoo.extendedterminal.menu.extendedterminal.ETTerminalMode;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.items.storage.ViewCellItem;
import appeng.util.prioritylist.IPartitionList;

/**
 * Auto-crafting executor for the Extended Terminal (ETTerminalMenu), dispatching by recipe type:
 *  - CRAFTING       -> existing 3x3 grid path (clears grid, fill from network, ET-capable batch)
 *  - STONECUTTING   -> stonecutter panel path (input slot, exact multiplier, result to inv/network)
 *  - SMITHING       -> smithing panel path (template+base+addition, exact multiplier)
 * Mirrors the JEI fork's Ae2CraftingGridCraftExecutor and ET's own fill packets.
 */
public class EtTerminalCraftingGridCraftExecutor implements ICraftingGridCraftExecutor {
    private static final int MAX_MULTIPLIER = 64;

    @Override
    public boolean canHandle(AbstractContainerMenu menu) {
        return menu instanceof ETTerminalMenu;
    }

    @Override
    public int craft(ServerPlayer player, int containerId, @Nullable ResourceLocation recipeId,
                     List<ItemStack> targetStacks, int multiplier) {
        AbstractContainerMenu menu = player.containerMenu;
        if (!(menu instanceof ETTerminalMenu etMenu) || menu.containerId != containerId || targetStacks.isEmpty()) {
            if (menu instanceof ETTerminalMenu etm) {
                TenshisJeiLog.info("[ET-jei] craft ignored: packet containerId {} vs menu containerId {}", containerId, etm.containerId);
            }
            return 0;
        }
        TenshisJeiLog.info("[ET-jei] craft request: multiplier={}, targets={}, recipeId={}", multiplier, targetStacks.size(), recipeId);

        if (recipeId != null) {
            var holder = player.level().getRecipeManager().byKey(recipeId).orElse(null);
            if (holder != null) {
                var type = holder.value().getType();
                if (type == RecipeType.STONECUTTING) {
                    return craftStonecutting(etMenu, player, containerId, recipeId, holder, multiplier);
                }
                if (type == RecipeType.SMITHING) {
                    return craftSmithing(etMenu, player, containerId, recipeId, holder, multiplier);
                }
            }
        }
        return craftCraftingGrid(etMenu, player, containerId, recipeId, targetStacks, multiplier);
    }

    // ------------------------------------------------------------------
    // CRAFTING (3x3 grid) path
    // ------------------------------------------------------------------
    private int craftCraftingGrid(ETTerminalMenu etMenu, ServerPlayer player, int containerId,
                                  @Nullable ResourceLocation recipeId, List<ItemStack> targetStacks, int multiplier) {
        forceMode(etMenu, ETTerminalMode.CRAFTING);

        InternalInventory grid = ((appeng.api.inventories.ISegmentedInventory) etMenu.getHost())
                .getSubInventory(ETMenuType.ET_TERMINAL.getCraftingInventory());
        if (grid == null || !clearGridToNetwork(etMenu, grid)) {
            TenshisJeiLog.info("[ET-jei] grid not fully cleared to network; aborting");
            player.displayClientMessage(Component.translatable("jei.ae2.crafting.grid_full"), false);
            return 0;
        }
        ServerBookmarkCraftingGridFill.ExternalIngredientSource networkSource = etMenu.getLinkStatus().connected()
                ? new EtNetworkMaterialSource(etMenu, player, targetStacks)
                : null;
        TenshisJeiLog.info("[ET-jei] link connected={}, networkSource={}", etMenu.getLinkStatus().connected(), networkSource != null);

        List<Slot> craftingSlots = etMenu.getSlots(etMenu.getCraftingGridSlotSemantic());
        List<Slot> resultSlots = etMenu.getSlots(etMenu.getOutputSlotSemantic());
        if (craftingSlots.isEmpty() || resultSlots.isEmpty()) {
            TenshisJeiLog.info("[ET-jei] no grid/result slots; aborting");
            return 0;
        }
        Slot resultSlot = resultSlots.getFirst();
        TenshiJeiCraftingTermBatchSupport etBatch = resultSlot instanceof TenshiJeiCraftingTermBatchSupport s ? s : null;
        if (etBatch == null && !(resultSlot instanceof IJeiCraftingTermSlotExtension)) {
            TenshisJeiLog.info("[ET-jei] result slot unsupported: {}", resultSlot.getClass().getName());
            player.displayClientMessage(Component.translatable("jei.ae2.crafting.unsupported"), false);
            return 0;
        }

        int remaining = multiplier == 0 ? MAX_MULTIPLIER : Math.min(MAX_MULTIPLIER, Math.max(1, multiplier));
        int crafted = 0;
        while (remaining > 0) {
            int filled = ServerBookmarkCraftingGridFill.fill(
                    etMenu,
                    containerId,
                    player.getInventory(),
                    player,
                    targetStacks,
                    remaining,
                    craftingSlots,
                    networkSource
            );
            if (filled <= 0) {
                TenshisJeiLog.info("[ET-jei] fill returned 0 (remaining={})", remaining);
                break;
            }
            etMenu.slotsChanged(player.getInventory());
            int done;
            if (etBatch != null) {
                done = etBatch.tenshiJei$craftBatch(etMenu, player, resultSlot.getItem(), Math.min(filled, remaining));
            } else {
                done = ((IJeiCraftingTermSlotExtension) resultSlot)
                        .jei$craftBatch(etMenu, player, resultSlot.getItem(), Math.min(filled, remaining));
            }
            TenshisJeiLog.info("[ET-jei] loop: filled={}, batchDone={} (remaining before={})", filled, done, remaining);
            if (done <= 0) {
                return crafted;
            }
            crafted += done;
            remaining -= done;
            etMenu.broadcastChanges();
        }
        if (crafted > 0) {
            player.getInventory().setChanged();
            etMenu.broadcastChanges();
        }
        TenshisJeiLog.info("[ET-jei] crafting finished: crafted={}", crafted);
        return crafted;
    }

    // ------------------------------------------------------------------
    // STONECUTTING panel path
    // ------------------------------------------------------------------
    private int craftStonecutting(ETTerminalMenu et, ServerPlayer player, int containerId,
                                  ResourceLocation recipeId, RecipeHolder<?> holder, int multiplier) {
        if (!(holder.value() instanceof StonecutterRecipe recipe)) {
            return 0;
        }
        Level level = player.level();
        forceMode(et, ETTerminalMode.STONECUTTING);
        et.setStoneCutterRecipeId(recipeId);
        TenshisJeiLog.info("[ET-jei] stonecutting: recipeId={}", recipeId);

        InternalInventory input = et.getStoneCutterInventory();
        if (input.size() < 1) {
            return 0;
        }
        MEStorage storage = et.getHost().getInventory();
        IEnergySource energy = et.getEnergySource();
        IActionSource src = et.getActionSource();
        IPartitionList filter = ViewCellItem.createItemFilter(et.getViewCells());
        Ingredient ingredient = recipe.getIngredients().getFirst();

        clearPanelSlot(et, player, input, 0, storage, energy, src);

        int remaining = multiplier == 0 ? MAX_MULTIPLIER : Math.min(MAX_MULTIPLIER, Math.max(1, multiplier));
        int crafted = 0;
        while (remaining > 0) {
            if (!et.getLinkStatus().connected()) {
                TenshisJeiLog.info("[ET-jei] stonecutting: link lost, stop at {}", crafted);
                break;
            }
            if (input.getStackInSlot(0).isEmpty()) {
                ItemStack got = extractFromNetwork(ingredient, filter, storage, energy, src);
                if (got.isEmpty()) {
                    got = extractFromPlayer(et, player, ingredient);
                }
                if (got.isEmpty()) {
                    TenshisJeiLog.info("[ET-jei] stonecutting: no ingredient, stop at {}", crafted);
                    break;
                }
                input.setItemDirect(0, got);
            }
            ItemStack in = input.getStackInSlot(0);
            TenshisJeiLog.info("[ET-jei] stonecutting round: in={}, remaining={}", in, remaining);
            SingleRecipeInput si = new SingleRecipeInput(in);
            if (!recipe.matches(si, level)) {
                TenshisJeiLog.info("[ET-jei] stonecutting: input {} does not match recipe, stop", in);
                break;
            }
            ItemStack result = recipe.assemble(si, level.registryAccess());
            if (result.isEmpty() || !canPlaceResult(et, player, result, storage, energy, src)) {
                TenshisJeiLog.info("[ET-jei] stonecutting: no room for result {}, stop at {}", result, crafted);
                break;
            }
            input.extractItem(0, 1, false);
            if (!placeResult(et, player, result, storage, energy, src)) {
                break;
            }
            crafted++;
            remaining--;
        }
        et.slotsChanged(input.toContainer());
        TenshisJeiLog.info("[ET-jei] stonecutting finished: crafted={}", crafted);
        return crafted;
    }

    // ------------------------------------------------------------------
    // SMITHING panel path
    // ------------------------------------------------------------------
    private int craftSmithing(ETTerminalMenu et, ServerPlayer player, int containerId,
                              ResourceLocation recipeId, RecipeHolder<?> holder, int multiplier) {
        if (!(holder.value() instanceof SmithingRecipe recipe)) {
            return 0;
        }
        Level level = player.level();
        forceMode(et, ETTerminalMode.SMITHING);
        TenshisJeiLog.info("[ET-jei] smithing: recipeId={}", recipeId);

        InternalInventory inputs = et.getSmithingInventory();
        if (inputs.size() < 3) {
            return 0;
        }
        MEStorage storage = et.getHost().getInventory();
        IEnergySource energy = et.getEnergySource();
        IActionSource src = et.getActionSource();
        IPartitionList filter = ViewCellItem.createItemFilter(et.getViewCells());
        // Vanilla smithing recipes return an empty ingredient list from getIngredients() in 1.21.1;
        // use ET's own adapter (template/base/addition order) like ET's fill packet does.
        var ingredients = ISmithingRecipeAdapter.of(recipe).getIngredients();
        if (ingredients.size() < 3) {
            TenshisJeiLog.info("[ET-jei] smithing: adapter returned {} ingredients, unsupported", ingredients.size());
            return 0;
        }

        for (int i = 0; i < 3; i++) {
            clearPanelSlot(et, player, inputs, i, storage, energy, src);
        }

        int remaining = multiplier == 0 ? MAX_MULTIPLIER : Math.min(MAX_MULTIPLIER, Math.max(1, multiplier));
        int crafted = 0;
        while (remaining > 0) {
            if (!et.getLinkStatus().connected()) {
                TenshisJeiLog.info("[ET-jei] smithing: link lost, stop at {}", crafted);
                break;
            }
            // ensure template/base/addition are stocked
            boolean missing = false;
            for (int i = 0; i < 3; i++) {
                Ingredient ingredient = ingredients.get(i);
                if (ingredient.isEmpty()) {
                    continue;
                }
                ItemStack slotStack = inputs.getStackInSlot(i);
                if (!slotStack.isEmpty() && ingredient.test(slotStack)) {
                    continue;
                }
                ItemStack got = extractFromNetwork(ingredient, filter, storage, energy, src);
                if (got.isEmpty()) {
                    got = extractFromPlayer(et, player, ingredient);
                }
                if (got.isEmpty()) {
                    missing = true;
                    TenshisJeiLog.info("[ET-jei] smithing: missing ingredient slot {} ({}), stop at {}", i, ingredient, crafted);
                    break;
                }
                inputs.setItemDirect(i, got);
            }
            if (missing) {
                break;
            }
            ItemStack t = inputs.getStackInSlot(0);
            ItemStack b = inputs.getStackInSlot(1);
            ItemStack a = inputs.getStackInSlot(2);
            SmithingRecipeInput si = new SmithingRecipeInput(t, b, a);
            if (!recipe.matches(si, level)) {
                TenshisJeiLog.info("[ET-jei] smithing: inputs do not match recipe, stop");
                break;
            }
            ItemStack result = recipe.assemble(si, level.registryAccess());
            if (result.isEmpty() || !canPlaceResult(et, player, result, storage, energy, src)) {
                TenshisJeiLog.info("[ET-jei] smithing: no room for result {}, stop at {}", result, crafted);
                break;
            }
            for (int i = 0; i < 3; i++) {
                if (!inputs.getStackInSlot(i).isEmpty()) {
                    inputs.extractItem(i, 1, false);
                }
            }
            // Restore remaining items (vanilla smithing keeps the template), like ET's own craft.
            var remainingItems = recipe.getRemainingItems(si);
            for (int i = 0; i < Math.min(3, remainingItems.size()); i++) {
                var rem = remainingItems.get(i);
                if (!rem.isEmpty()) {
                    if (inputs.getStackInSlot(i).isEmpty()) {
                        inputs.setItemDirect(i, rem);
                    } else if (!player.getInventory().add(rem)) {
                        player.drop(rem, false);
                    }
                }
            }
            if (!placeResult(et, player, result, storage, energy, src)) {
                break;
            }
            crafted++;
            remaining--;
        }
        et.slotsChanged(inputs.toContainer());
        TenshisJeiLog.info("[ET-jei] smithing finished: crafted={}", crafted);
        return crafted;
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------
    private static void forceMode(ETTerminalMenu et, ETTerminalMode mode) {
        if (et.getMode() != mode) {
            TenshisJeiLog.info("[ET-jei] terminal mode {} -> {}", et.getMode(), mode);
            et.setMode(mode);
        }
    }

    private static void clearPanelSlot(ETTerminalMenu et, ServerPlayer player, InternalInventory inv,
                                       int idx, MEStorage storage, IEnergySource energy, IActionSource src) {
        ItemStack stack = inv.getStackInSlot(idx);
        if (stack.isEmpty()) {
            return;
        }
        var key = AEItemKey.of(stack);
        long inserted = StorageHelper.poweredInsert(energy, storage, key, stack.getCount(), src);
        if (inserted > 0) {
            stack = stack.copy();
            stack.shrink((int) inserted);
            inv.setItemDirect(idx, stack);
        }
        if (!stack.isEmpty()) {
            player.getInventory().add(stack);
            inv.setItemDirect(idx, ItemStack.EMPTY);
        }
    }

    private static ItemStack extractFromNetwork(Ingredient ingredient, IPartitionList filter,
                                                MEStorage storage, IEnergySource energy, IActionSource src) {
        var snapshot = storage.getAvailableStacks();
        for (var item : ingredient.getItems()) {
            var key = AEItemKey.of(item);
            if (key == null || (filter != null && !filter.isEmpty() && !filter.isListed(key))) {
                continue;
            }
            var fuzzy = snapshot.findFuzzy(key, FuzzyMode.IGNORE_ALL);
            for (var e : fuzzy) {
                if (!(e.getKey() instanceof AEItemKey candidate)) {
                    continue;
                }
                if (!candidate.matches(ingredient)) {
                    continue;
                }
                long got = StorageHelper.poweredExtraction(energy, storage, candidate, 1, src);
                if (got > 0) {
                    return candidate.toStack((int) got);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack extractFromPlayer(ETTerminalMenu et, ServerPlayer player, Ingredient ingredient) {
        var playerInv = player.getInventory();
        for (int i = 0; i < playerInv.items.size(); i++) {
            if (et.isPlayerInventorySlotLocked(i)) {
                continue;
            }
            var item = playerInv.getItem(i);
            if (ingredient.test(item)) {
                var result = item.split(1);
                if (!result.isEmpty()) {
                    return result;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean canPlaceResult(ETTerminalMenu et, ServerPlayer player, ItemStack result,
                                          MEStorage storage, IEnergySource energy, IActionSource src) {
        if (result.isEmpty()) {
            return true;
        }
        int room = getInsertableCount(player.getInventory(), result);
        if (room >= result.getCount()) {
            return true;
        }
        if (!et.getLinkStatus().connected()) {
            return false;
        }
        long need = result.getCount() - room;
        long simulated = StorageHelper.poweredInsert(energy, storage, AEItemKey.of(result), need, src,
                Actionable.SIMULATE);
        return simulated >= need;
    }

    private static boolean placeResult(ETTerminalMenu et, ServerPlayer player, ItemStack result,
                                       MEStorage storage, IEnergySource energy, IActionSource src) {
        if (result.isEmpty()) {
            return true;
        }
        int room = getInsertableCount(player.getInventory(), result);
        if (room > 0) {
            player.getInventory().add(result.copyWithCount(room));
            result = result.copy();
            result.shrink(room);
        }
        if (result.isEmpty()) {
            return true;
        }
        if (!et.getLinkStatus().connected()) {
            return false;
        }
        long inserted = StorageHelper.poweredInsert(energy, storage, AEItemKey.of(result), result.getCount(), src);
        return inserted >= result.getCount();
    }

    private static int getInsertableCount(Inventory inventory, ItemStack stack) {
        int remaining = stack.getCount();
        for (ItemStack slotStack : inventory.items) {
            if (remaining <= 0) {
                break;
            }
            if (slotStack.isEmpty()) {
                remaining = Math.max(0, remaining - stack.getMaxStackSize());
            } else if (ItemStack.isSameItemSameComponents(slotStack, stack) && slotStack.getCount() < slotStack.getMaxStackSize()) {
                remaining = Math.max(0, remaining - (slotStack.getMaxStackSize() - slotStack.getCount()));
            }
        }
        return Math.max(0, stack.getCount() - remaining);
    }

    private static boolean clearGridToNetwork(ETTerminalMenu etMenu, InternalInventory grid) {
        boolean hasItems = false;
        for (int i = 0; i < grid.size(); i++) {
            if (!grid.getStackInSlot(i).isEmpty()) {
                hasItems = true;
                break;
            }
        }
        if (!hasItems) {
            return true;
        }
        MEStorage storage = etMenu.getHost().getInventory();
        IActionSource src = etMenu.getActionSource();
        for (int i = 0; i < grid.size(); i++) {
            ItemStack stack = grid.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            AEItemKey key = AEItemKey.of(stack);
            long inserted = storage.insert(key, stack.getCount(), Actionable.MODULATE, src);
            if (inserted < stack.getCount()) {
                grid.setItemDirect(i, stack.copyWithCount((int) (stack.getCount() - inserted)));
                return false;
            }
            grid.setItemDirect(i, ItemStack.EMPTY);
        }
        etMenu.slotsChanged(grid.toContainer());
        return true;
    }
}
