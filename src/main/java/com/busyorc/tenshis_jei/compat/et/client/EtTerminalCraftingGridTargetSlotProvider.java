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
 * The crafting-grid slots are ALWAYS included (workbench recipes need a 3x3 grid even
 * while the terminal is in smithing/stonecutting mode), with the panel slots appended
 * first when the terminal is on a panel screen so smithing/stonecutting recipe
 * fills/ghosts still target the right panel. The planner falls back to the first N
 * slots, so workbench recipes take the 3x3 crafting grid and panel recipes take the
 * panel slots.
 */
public class EtTerminalCraftingGridTargetSlotProvider implements BookmarkGhostOverlayTargetSlots.Provider {
    @Override
    public Optional<List<Slot>> getCraftingGridSlots(AbstractContainerMenu menu) {
        if (!(menu instanceof ETTerminalMenu et)) {
            return Optional.empty();
        }
        List<Slot> slots = new ArrayList<>();
        switch (et.getMode()) {
            case SMITHING -> {
                slots.addAll(et.getSlots(ETSlotSemantics.SMITHING_TABLE_TEMPLATE));
                slots.addAll(et.getSlots(ETSlotSemantics.SMITHING_TABLE_BASE));
                slots.addAll(et.getSlots(ETSlotSemantics.SMITHING_TABLE_ADDITION));
            }
            case STONECUTTING -> slots.addAll(et.getSlots(ETSlotSemantics.STONECUTTING_INPUT));
            default -> {
            }
        }
        slots.addAll(et.getSlots(et.getCraftingGridSlotSemantic()));
        return Optional.of(slots);
    }
}