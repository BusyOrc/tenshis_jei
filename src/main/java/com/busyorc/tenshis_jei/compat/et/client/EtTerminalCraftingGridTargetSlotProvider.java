package com.busyorc.tenshis_jei.compat.et.client;

import me.myogoo.extendedterminal.menu.ETSlotSemantics;
import me.myogoo.extendedterminal.menu.extendedterminal.ETTerminalMenu;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkGhostOverlayTargetSlots;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Exposes the Extended Terminal's input slots to the JEI bookmark auto-craft planner.
 * <p>
 * 重要：JEIU 19.54 的 BookmarkCraftingGridFill.createCandidateSlots 只接受
 * targetSlotCount = 4 或 9（其余一律 columns=0 -> fill 为空 -> **客户端根本不会发包**），
 * 所以面板模式不能返回"面板槽 + 完整 9 格网格"（那是 10/12），否则锻造/切石配方永远不执行。
 * <p>
 * 规则：面板槽放在最前（JEIU 按索引把配方输入映射到目标槽），再用终端合成网格槽补到 9 格。
 * 多出来的补位槽在末尾：幽灵覆盖层只取 min(配方输入数, 目标槽数) 个，不会多画；
 * 合成（CRAFTING）模式则保持原样返回 9 格网格。
 */
public class EtTerminalCraftingGridTargetSlotProvider implements BookmarkGhostOverlayTargetSlots.Provider {
    /** JEIU 只支持 4/9 两种网格尺寸；ET 面板按 3x3(9) 对齐最通用。 */
    private static final int TARGET_SLOT_COUNT = 9;

    @Override
    public Optional<List<Slot>> getCraftingGridSlots(AbstractContainerMenu menu) {
        if (!(menu instanceof ETTerminalMenu et)) {
            return Optional.empty();
        }
        List<Slot> panel = new ArrayList<>();
        switch (et.getMode()) {
            case SMITHING -> {
                panel.addAll(et.getSlots(ETSlotSemantics.SMITHING_TABLE_TEMPLATE));
                panel.addAll(et.getSlots(ETSlotSemantics.SMITHING_TABLE_BASE));
                panel.addAll(et.getSlots(ETSlotSemantics.SMITHING_TABLE_ADDITION));
            }
            case STONECUTTING -> panel.addAll(et.getSlots(ETSlotSemantics.STONECUTTING_INPUT));
            default -> {
            }
        }
        List<Slot> grid = et.getSlots(et.getCraftingGridSlotSemantic());
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
            // 网格槽不足 9 个时用面板槽循环补位（正常情况下不会走到这里）
            for (int i = 0; slots.size() < TARGET_SLOT_COUNT; i++) {
                slots.add(panel.get(i % panel.size()));
            }
        }
        com.busyorc.tenshis_jei.compat.et.EtLog.info("[ET-jei] targetSlotsProvider(ET): mode={} panel={} slots={}",
            et.getMode(), panel.size(), slots.size());
        return Optional.of(slots);
    }
}