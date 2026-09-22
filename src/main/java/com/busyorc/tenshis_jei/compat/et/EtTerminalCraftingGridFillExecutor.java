package com.busyorc.tenshis_jei.compat.et;

import com.busyorc.tenshis_jei.TenshisJeiLog;
import me.myogoo.extendedterminal.menu.ETSlotSemantics;
import me.myogoo.extendedterminal.menu.extendedterminal.ETTerminalMenu;
import me.myogoo.extendedterminal.menu.extendedterminal.ETTerminalMode;
import mezz.jei.common.bookmarks.ICraftingGridFillExecutor;
import mezz.jei.common.bookmarks.ServerBookmarkCraftingGridFill;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Fill-only executor for the Extended Terminal.
 * <p>
 * 新版 JEIU 把"把配方材料填进工作站格子"（幽灵覆盖层/点击填充）从 craft 拆成了独立动作：
 * 客户端发 PacketFillCraftingGrid -> 服务端 CraftingGridFillExecutors.fill(...)，
 * 没有注册 fill executor 时只走原版格子填充，ET 终端因此完全不响应（联动失效）。
 * 这里为 ET 终端补上 fill executor：把配方材料（玩家物品栏 + 终端联网存储）按当前模式
 * 填进对应的输入格（加工台 3x3 / 锻造台三槽 / 切石机输入槽），但不执行合成。
 * 复用 fork 的 ServerBookmarkCraftingGridFill，与 craft executor 保持同一套填充语义。
 */
public class EtTerminalCraftingGridFillExecutor implements ICraftingGridFillExecutor {

    @Override
    public boolean canHandle(AbstractContainerMenu menu) {
        return menu instanceof ETTerminalMenu;
    }

    @Override
    public int fill(ServerPlayer player, int containerId, List<ItemStack> targetStacks, int multiplier) {
        AbstractContainerMenu menu = player.containerMenu;
        if (!(menu instanceof ETTerminalMenu etMenu) || menu.containerId != containerId || targetStacks.isEmpty()) {
            return 0;
        }
        List<Slot> slots = resolveTargetSlots(etMenu);
        if (slots.isEmpty()) {
            TenshisJeiLog.info("[ET-jei] fill: no target slots for mode {}", etMenu.getMode());
            return 0;
        }
        EtLog.info("[ET-jei] fill request: mode={}, multiplier={}, targets={}, slots={}, connected={}",
            etMenu.getMode(), multiplier, targetStacks.size(), slots.size(), etMenu.getLinkStatus().connected());

        ServerBookmarkCraftingGridFill.ExternalIngredientSource networkSource = etMenu.getLinkStatus().connected()
            ? new EtNetworkMaterialSource(etMenu, player, targetStacks)
            : null;

        int filled = ServerBookmarkCraftingGridFill.fill(
            menu,
            containerId,
            player.getInventory(),
            player,
            targetStacks,
            multiplier,
            slots,
            networkSource
        );
        if (filled > 0) {
            etMenu.slotsChanged(player.getInventory());
            etMenu.broadcastChanges();
        }
        EtLog.info("[ET-jei] fill finished: filled={}", filled);
        return filled;
    }

    /**
     * 只按当前模式选目标格（不再依赖 targetStacks 长度）：
     * 锻造台 -> 模板/基底/添加三槽；切石机 -> 输入槽；其余（含工作台 3x3）-> 合成网格槽。
     * 客户端现在按 JEIU 只支持 4/9 的限制返回 9 个目标槽，长度已不能用来判断面板类型。
     */
    private static List<Slot> resolveTargetSlots(ETTerminalMenu et) {
        List<Slot> slots = new ArrayList<>();
        switch (et.getMode()) {
            case SMITHING -> {
                slots.addAll(et.getSlots(ETSlotSemantics.SMITHING_TABLE_TEMPLATE));
                slots.addAll(et.getSlots(ETSlotSemantics.SMITHING_TABLE_BASE));
                slots.addAll(et.getSlots(ETSlotSemantics.SMITHING_TABLE_ADDITION));
            }
            case STONECUTTING -> slots.addAll(et.getSlots(ETSlotSemantics.STONECUTTING_INPUT));
            default -> slots.addAll(et.getSlots(et.getCraftingGridSlotSemantic()));
        }
        if (slots.isEmpty()) {
            slots.addAll(et.getSlots(et.getCraftingGridSlotSemantic()));
        }
        return slots;
    }
}
