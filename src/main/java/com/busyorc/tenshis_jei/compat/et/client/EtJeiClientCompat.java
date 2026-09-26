package com.busyorc.tenshis_jei.compat.et.client;

import com.busyorc.tenshis_jei.TenshisJei;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAvailableStacksProviders;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkGhostOverlayTargetSlots;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Registers the ET terminal providers into the JEI fork's internal registries.
 * Client side only (these registries live in the fork's GUI module).
 */
@EventBusSubscriber(modid = TenshisJei.MOD_ID, value = Dist.CLIENT)
public final class EtJeiClientCompat {
    private EtJeiClientCompat() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        BookmarkAvailableStacksProviders.registerProvider(new EtTerminalAvailableStacksProvider());
        BookmarkGhostOverlayTargetSlots.registerProvider(new EtTerminalCraftingGridTargetSlotProvider());
        com.busyorc.tenshis_jei.compat.et.EtLog.info("[ET-jei] registered ET client providers: availableStacks + targetSlots");
        // WCWT（可选前置）：注册其终端的目标槽位 provider
        com.busyorc.tenshis_jei.compat.wcwt.WcwtCompat.registerClient();
    }
}
