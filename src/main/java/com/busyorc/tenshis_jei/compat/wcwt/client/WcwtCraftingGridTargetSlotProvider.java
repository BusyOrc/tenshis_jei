package com.busyorc.tenshis_jei.compat.wcwt.client;

import com.busyorc.tenshis_jei.compat.et.EtLog;
import com.lhy.wcwt.menu.WcwtSlotSemantics;
import com.lhy.wcwt.menu.WirelessComprehensiveWorkTerminalMenu;
import com.lhy.wcwt.menu.WirelessComprehensiveWorkTerminalMenu.ManualWorkspaceMode;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkGhostOverlayTargetSlots;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import appeng.menu.SlotSemantics;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 把 WCWT 终端当前手动工作区的输入槽交给 JEIU 的收藏组/配方树自动合成规划。
 * <p>
 * JEIU 只接受 targetSlotCount = 4 或 9，所以锻造台模式返回"模板/基底/添加三槽 + 用合成网格槽补到 9"；
 * 其它模式（含工作台）返回 3x3 合成网格槽。
 */
public class WcwtCraftingGridTargetSlotProvider implements BookmarkGhostOverlayTargetSlots.Provider {
    private static final int TARGET_SLOT_COUNT = 9;

    @Override
    public Optional<List<Slot>> getCraftingGridSlots(AbstractContainerMenu menu) {
        if (!(menu instanceof WirelessComprehensiveWorkTerminalMenu wcwt)) {
            return Optional.empty();
        }
        List<Slot> grid = wcwt.getSlots(SlotSemantics.CRAFTING_GRID);
        List<Slot> panel = new ArrayList<>();
        if (wcwt.getManualWorkspaceMode() == ManualWorkspaceMode.SMITHING) {
            panel.addAll(wcwt.getSlots(WcwtSlotSemantics.WCWT_MANUAL_SMITHING_TEMPLATE));
            panel.addAll(wcwt.getSlots(WcwtSlotSemantics.WCWT_MANUAL_SMITHING_BASE));
            panel.addAll(wcwt.getSlots(WcwtSlotSemantics.WCWT_MANUAL_SMITHING_ADDITION));
        }
        List<Slot> slots = new ArrayList<>();
        if (panel.isEmpty()) {
            slots.addAll(grid);
        } else {
            slots.addAll(panel);
            for (Slot slot : grid) {
                if (slots.size() >= TARGET_SLOT_COUNT) {
                    break;
                }
                slots.add(slot);
            }
            for (int i = 0; slots.size() < TARGET_SLOT_COUNT; i++) {
                slots.add(panel.get(i % panel.size()));
            }
        }
        EtLog.info("[ET-jei] wcwt targetSlotsProvider: mode={} panel={} slots={}",
            wcwt.getManualWorkspaceMode(), panel.size(), slots.size());
        return Optional.of(slots);
    }
}
