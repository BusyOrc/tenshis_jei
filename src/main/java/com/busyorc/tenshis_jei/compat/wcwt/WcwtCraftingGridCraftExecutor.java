package com.busyorc.tenshis_jei.compat.wcwt;

import com.busyorc.tenshis_jei.compat.et.EtLog;
import com.lhy.wcwt.menu.WcwtSlotSemantics;
import com.lhy.wcwt.menu.WirelessComprehensiveWorkTerminalMenu;
import com.lhy.wcwt.menu.WirelessComprehensiveWorkTerminalMenu.ManualWorkspaceMode;
import com.busyorc.tenshis_jei.compat.et.TenshiJeiCraftingTermBatchSupport;
import mezz.jei.common.bookmarks.ICraftingGridCraftExecutor;
import mezz.jei.common.bookmarks.ServerBookmarkCraftingGridFill;
import mezz.jei.neoforge.compat.ae2.IJeiCraftingTermSlotExtension;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.menu.SlotSemantics;

import java.util.ArrayList;
import java.util.List;

/**
 * WCWT（无线综合工作终端）的服务端自动合成执行器，功能与 ET 的对应执行器一致：
 * <ul>
 *   <li>配方是工作台(crafting) -> 自动切到 CRAFTING 模式，清理并填充 3x3 合成网格，批量取出结果；</li>
 *   <li>配方是锻造台(smithing) -> 自动切到 SMITHING 模式，填模板/基底/添加三槽后取走结果槽触发合成；</li>
 *   <li>切石机：WCWT 的手动工作区**没有**切石面板（切石只存在于样板编码标签），故不支持，直接返回 0。</li>
 * </ul>
 */
public class WcwtCraftingGridCraftExecutor implements ICraftingGridCraftExecutor {
    private static final int MAX_MULTIPLIER = 64;

    @Override
    public boolean canHandle(AbstractContainerMenu menu) {
        return menu instanceof WirelessComprehensiveWorkTerminalMenu;
    }

    @Override
    public int craft(ServerPlayer player, int containerId, @Nullable ResourceLocation recipeId,
                     List<ItemStack> targetStacks, int multiplier) {
        AbstractContainerMenu menu = player.containerMenu;
        if (!(menu instanceof WirelessComprehensiveWorkTerminalMenu wcwt) || menu.containerId != containerId
            || targetStacks.isEmpty()) {
            EtLog.info("[ET-jei] wcwt craft ignored: menu={} containerId={}/{} targetsEmpty={}",
                menu == null ? "null" : menu.getClass().getSimpleName(),
                containerId, menu == null ? -1 : menu.containerId, targetStacks.isEmpty());
            return 0;
        }
        EtLog.info("[ET-jei] wcwt craft request: multiplier={} targets={} recipeId={} mode={}",
            multiplier, targetStacks.size(), recipeId, wcwt.getManualWorkspaceMode());

        if (recipeId != null) {
            var holder = player.level().getRecipeManager().byKey(recipeId).orElse(null);
            if (holder != null) {
                RecipeType<?> type = holder.value().getType();
                if (type == RecipeType.SMITHING && holder.value() instanceof SmithingRecipe smithingRecipe) {
                    return craftSmithing(wcwt, player, containerId, targetStacks, multiplier, smithingRecipe);
                }
                if (type == RecipeType.STONECUTTING) {
                    EtLog.info("[ET-jei] wcwt: 该终端没有手动切石面板，无法执行 {}", recipeId);
                    player.displayClientMessage(Component.literal("[ET-jei] WCWT 终端没有手动切石面板，无法自动执行切石配方"), false);
                    return 0;
                }
            }
        }
        return craftCraftingGrid(wcwt, player, containerId, targetStacks, multiplier);
    }

    // ------------------------------------------------------------------
    // 工作台：3x3 合成网格
    // ------------------------------------------------------------------
    private int craftCraftingGrid(WirelessComprehensiveWorkTerminalMenu wcwt, ServerPlayer player,
                                  int containerId, List<ItemStack> targetStacks, int multiplier) {
        setMode(wcwt, ManualWorkspaceMode.CRAFTING);
        InternalInventory grid = wcwt.getCraftingMatrix();
        if (grid == null || !clearGridToNetwork(wcwt, grid)) {
            player.displayClientMessage(Component.translatable("jei.ae2.crafting.grid_full"), false);
            return 0;
        }
        List<Slot> craftingSlots = wcwt.getSlots(SlotSemantics.CRAFTING_GRID);
        List<Slot> resultSlots = wcwt.getSlots(SlotSemantics.CRAFTING_RESULT);
        if (craftingSlots.isEmpty() || resultSlots.isEmpty()) {
            return 0;
        }
        Slot resultSlot = resultSlots.getFirst();
        TenshiJeiCraftingTermBatchSupport batch = resultSlot instanceof TenshiJeiCraftingTermBatchSupport b ? b : null;
        if (batch == null && !(resultSlot instanceof IJeiCraftingTermSlotExtension)) {
            EtLog.info("[ET-jei] wcwt: result slot unsupported: {}", resultSlot.getClass().getName());
            player.displayClientMessage(Component.translatable("jei.ae2.crafting.unsupported"), false);
            return 0;
        }
        ServerBookmarkCraftingGridFill.ExternalIngredientSource networkSource = wcwt.getLinkStatus().connected()
            ? new WcwtNetworkMaterialSource(wcwt, player) : null;

        int remaining = multiplier == 0 ? MAX_MULTIPLIER : Math.min(MAX_MULTIPLIER, Math.max(1, multiplier));
        int crafted = 0;
        while (remaining > 0) {
            int filled = ServerBookmarkCraftingGridFill.fill(
                wcwt, containerId, player.getInventory(), player, targetStacks, remaining, craftingSlots, networkSource);
            if (filled <= 0) {
                break;
            }
            wcwt.slotsChanged(player.getInventory());
            int done = batch != null
                ? batch.tenshiJei$craftBatch(wcwt, player, resultSlot.getItem(), Math.min(filled, remaining))
                : ((IJeiCraftingTermSlotExtension) resultSlot)
                    .jei$craftBatch(wcwt, player, resultSlot.getItem(), Math.min(filled, remaining));
            if (done <= 0) {
                return crafted;
            }
            crafted += done;
            remaining -= done;
            wcwt.broadcastChanges();
        }
        if (crafted > 0) {
            player.getInventory().setChanged();
            wcwt.broadcastChanges();
        }
        EtLog.info("[ET-jei] wcwt crafting finished: crafted={}", crafted);
        return crafted;
    }

    // ------------------------------------------------------------------
    // 锻造台：模板 / 基底 / 添加 三槽
    //
    // 注意：AE2 菜单里"点击结果槽取件"并不可靠（WCWT 的手动工作区结果槽走 AE2 的槽位处理，
    // 走 clicked(PICKUP) 拿不到 carried）。因此这里与 ET 的锻造路径保持一致：
    // 填好三槽后**由我们按配方自行组装结果**、消耗输入、把产物放进背包/网络。
    // ------------------------------------------------------------------
    private int craftSmithing(WirelessComprehensiveWorkTerminalMenu wcwt, ServerPlayer player, int containerId,
                              List<ItemStack> targetStacks, int multiplier, SmithingRecipe recipe) {
        setMode(wcwt, ManualWorkspaceMode.SMITHING);

        List<Slot> inputs = new ArrayList<>();
        inputs.addAll(wcwt.getSlots(WcwtSlotSemantics.WCWT_MANUAL_SMITHING_TEMPLATE));
        inputs.addAll(wcwt.getSlots(WcwtSlotSemantics.WCWT_MANUAL_SMITHING_BASE));
        inputs.addAll(wcwt.getSlots(WcwtSlotSemantics.WCWT_MANUAL_SMITHING_ADDITION));
        if (inputs.size() < 3) {
            EtLog.info("[ET-jei] wcwt smithing: 输入槽不足 (inputs={})", inputs.size());
            return 0;
        }
        boolean connected = wcwt.getLinkStatus().connected();
        ServerBookmarkCraftingGridFill.ExternalIngredientSource networkSource =
            connected ? new WcwtNetworkMaterialSource(wcwt, player) : null;

        int remaining = multiplier == 0 ? MAX_MULTIPLIER : Math.min(MAX_MULTIPLIER, Math.max(1, multiplier));
        int crafted = 0;
        while (remaining > 0) {
            // 每轮只填"一次合成"的量，避免一次塞太多
            int filled = ServerBookmarkCraftingGridFill.fill(
                wcwt, containerId, player.getInventory(), player, targetStacks, 1, inputs, networkSource);
            if (filled <= 0) {
                EtLog.info("[ET-jei] wcwt smithing: 填料失败（材料不足？）remaining={}", remaining);
                break;
            }
            wcwt.slotsChanged(player.getInventory());

            SmithingRecipeInput input = new SmithingRecipeInput(
                inputs.get(0).getItem(), inputs.get(1).getItem(), inputs.get(2).getItem());
            if (!recipe.matches(input, player.level())) {
                EtLog.info("[ET-jei] wcwt smithing: 三槽内容与配方不匹配 t={} b={} a={}",
                    inputs.get(0).getItem(), inputs.get(1).getItem(), inputs.get(2).getItem());
                break;
            }
            ItemStack result = recipe.assemble(input, player.level().registryAccess());
            if (result.isEmpty() || !placeResult(wcwt, player, result.copy())) {
                EtLog.info("[ET-jei] wcwt smithing: 产物无法放置/为空 -> {}", result);
                break;
            }
            // 消耗一份输入，并处理余留物（如锻造模板等由配方决定）
            List<ItemStack> remainders = recipe.getRemainingItems(input);
            for (int i = 0; i < 3; i++) {
                if (!inputs.get(i).getItem().isEmpty()) {
                    inputs.get(i).remove(1);
                }
            }
            for (int i = 0; i < 3 && i < remainders.size(); i++) {
                ItemStack rem = remainders.get(i);
                if (rem.isEmpty()) {
                    continue;
                }
                if (inputs.get(i).getItem().isEmpty()) {
                    inputs.get(i).set(rem);
                } else if (!player.getInventory().add(rem)) {
                    player.drop(rem, false);
                }
            }
            crafted++;
            remaining--;
            wcwt.slotsChanged(player.getInventory());
            wcwt.broadcastChanges();
        }
        if (crafted > 0) {
            player.getInventory().setChanged();
            wcwt.broadcastChanges();
        }
        EtLog.info("[ET-jei] wcwt smithing finished: crafted={}", crafted);
        return crafted;
    }

    /** 把产物放进玩家背包，放不下的部分塞进终端联网存储。 */
    private static boolean placeResult(WirelessComprehensiveWorkTerminalMenu wcwt, ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        if (player.getInventory().add(stack)) {
            return true;
        }
        int remain = stack.getCount();
        if (remain > 0 && wcwt.getLinkStatus().connected()) {
            long inserted = StorageHelper.poweredInsert(
                wcwt.getEnergySource(), wcwt.getHost().getInventory(), AEItemKey.of(stack), remain, wcwt.getActionSource());
            remain -= (int) inserted;
        }
        return remain <= 0;
    }

    private static void setMode(WirelessComprehensiveWorkTerminalMenu wcwt, ManualWorkspaceMode mode) {
        if (wcwt.getManualWorkspaceMode() != mode) {
            EtLog.info("[ET-jei] wcwt manual workspace {} -> {}", wcwt.getManualWorkspaceMode(), mode);
            wcwt.setManualWorkspaceMode(mode);
        }
    }

    private static boolean clearGridToNetwork(WirelessComprehensiveWorkTerminalMenu wcwt, InternalInventory grid) {
        MEStorage storage = wcwt.getHost().getInventory();
        IActionSource src = wcwt.getActionSource();
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
        wcwt.slotsChanged(grid.toContainer());
        return true;
    }
}
