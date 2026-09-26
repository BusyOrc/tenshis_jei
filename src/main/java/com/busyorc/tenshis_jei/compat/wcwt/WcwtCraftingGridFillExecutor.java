package com.busyorc.tenshis_jei.compat.wcwt;

import com.busyorc.tenshis_jei.compat.et.EtLog;
import com.lhy.wcwt.menu.WcwtSlotSemantics;
import com.lhy.wcwt.menu.WirelessComprehensiveWorkTerminalMenu;
import com.lhy.wcwt.menu.WirelessComprehensiveWorkTerminalMenu.ManualWorkspaceMode;
import mezz.jei.common.bookmarks.ICraftingGridFillExecutor;
import mezz.jei.common.bookmarks.ServerBookmarkCraftingGridFill;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import appeng.menu.SlotSemantics;

import java.util.ArrayList;
import java.util.List;

/** WCWT 的"只填充不合成"执行器（新版 JEIU 的 PacketFillCraftingGrid 路径），与 ET 的对应执行器一致。 */
public class WcwtCraftingGridFillExecutor implements ICraftingGridFillExecutor {

    @Override
    public boolean canHandle(AbstractContainerMenu menu) {
        return menu instanceof WirelessComprehensiveWorkTerminalMenu;
    }

    @Override
    public int fill(ServerPlayer player, int containerId, List<ItemStack> targetStacks, int multiplier) {
        AbstractContainerMenu menu = player.containerMenu;
        if (!(menu instanceof WirelessComprehensiveWorkTerminalMenu wcwt) || menu.containerId != containerId
            || targetStacks.isEmpty()) {
            return 0;
        }
        List<Slot> slots = resolveTargetSlots(wcwt);
        if (slots.isEmpty()) {
            return 0;
        }
        EtLog.info("[ET-jei] wcwt fill request: mode={} multiplier={} targets={} slots={} connected={}",
            wcwt.getManualWorkspaceMode(), multiplier, targetStacks.size(), slots.size(),
            wcwt.getLinkStatus().connected());

        ServerBookmarkCraftingGridFill.ExternalIngredientSource networkSource = wcwt.getLinkStatus().connected()
            ? new WcwtNetworkMaterialSource(wcwt, player) : null;
        int filled = ServerBookmarkCraftingGridFill.fill(
            wcwt, containerId, player.getInventory(), player, targetStacks, multiplier, slots, networkSource);
        if (filled > 0) {
            wcwt.slotsChanged(player.getInventory());
            wcwt.broadcastChanges();
        }
        EtLog.info("[ET-jei] wcwt fill finished: filled={}", filled);
        return filled;
    }

    /** 只按当前手动工作区模式选目标槽：SMITHING -> 模板/基底/添加；其余 -> 3x3 合成网格。 */
    private static List<Slot> resolveTargetSlots(WirelessComprehensiveWorkTerminalMenu wcwt) {
        List<Slot> slots = new ArrayList<>();
        if (wcwt.getManualWorkspaceMode() == ManualWorkspaceMode.SMITHING) {
            slots.addAll(wcwt.getSlots(WcwtSlotSemantics.WCWT_MANUAL_SMITHING_TEMPLATE));
            slots.addAll(wcwt.getSlots(WcwtSlotSemantics.WCWT_MANUAL_SMITHING_BASE));
            slots.addAll(wcwt.getSlots(WcwtSlotSemantics.WCWT_MANUAL_SMITHING_ADDITION));
        }
        if (slots.isEmpty()) {
            slots.addAll(wcwt.getSlots(SlotSemantics.CRAFTING_GRID));
        }
        return slots;
    }
}
