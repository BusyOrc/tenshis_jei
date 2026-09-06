package com.busyorc.tenshis_jei;

import com.busyorc.tenshis_jei.compat.ae2.WirelessExternalStorageSnapshotProvider;
import com.busyorc.tenshis_jei.client.TenshisJeiKeys;
import com.busyorc.tenshis_jei.compat.tinkers.AnvilAutoReSlot;
import com.busyorc.tenshis_jei.network.WirelessNetworkHandler;
import mezz.jei.gui.bookmarks.chain.BookmarkExternalStorageSnapshots;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

/** Client-only entry (invoked via DistExecutor on Dist.CLIENT). */
public final class TenshisJeiClient {
    private TenshisJeiClient() {
    }

    public static void registerClient(IEventBus modEventBus) {
        // Reb bindable hotkey: generate tinkers tool modifier recipe chains (default SHIFT+F).
        modEventBus.addListener(TenshisJeiKeys::register);
        // Expose the wireless ME network as a bookmark-pull external-storage snapshot.
        try {
            BookmarkExternalStorageSnapshots.registerProvider(new WirelessExternalStorageSnapshotProvider());
            TenshisJeiLog.info("Registered wireless external-storage snapshot provider for JEI bookmark pull.");
        } catch (RuntimeException e) {
            TenshisJeiLog.warn("Wireless snapshot provider registration failed: " + e);
        }
        // Periodically request the server to enumerate the wireless ME network contents.
        MinecraftForge.EVENT_BUS.addListener(WirelessNetworkHandler::onClientTick);
        // Anvil auto-craft: keep the enhanced tool in slot0 between modifier steps.
        MinecraftForge.EVENT_BUS.addListener(AnvilAutoReSlot::onClientTick);
    }
}
