package com.busyorc.tenshis_jei;

import com.busyorc.tenshis_jei.compat.ae2.WirelessBookmarkPullTransferHandler;
import com.busyorc.tenshis_jei.network.WirelessNetworkHandler;
import mezz.jei.common.bookmarks.ServerBookmarkPullTransfers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Tenshi's JEI Addon - Forge 1.20.1 port.
 * Registers: ME wireless bookmark pull handler (EAEP/AE2), Curios item locator for AE2
 * menu-host resolution, and (client side) a BookmarkExternalStorageSnapshots.Provider that
 * exposes the wireless ME network snapshot (fed by the periodic server sync).
 */
@Mod(TenshisJei.MOD_ID)
public class TenshisJei {

    public static final String MOD_ID = "tenshis_jei_addon";
    public static final String MOD_NAME = "Tenshi's JEI Addon";
    public static final String VERSION = "1.0.1";

    public TenshisJei() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, TenshisJeiConfig.SPEC, TenshisJei.MOD_ID + ".toml");

        // Wireless ME pull: registered handler runs after the fork's own AE2 handler.
        try {
            ServerBookmarkPullTransfers.registerHandler(new WirelessBookmarkPullTransferHandler());
        } catch (RuntimeException e) {
            TenshisJeiLog.warn("WirelessBookmarkPullTransferHandler registration failed: " + e);
        }

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        WirelessNetworkHandler.register(modEventBus);

        // Client-only: register the external-storage snapshot provider for bookmark pull
        // (V/shift+V in the favorites panel pulls from the wireless ME network).
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> TenshisJeiClient::registerClient);

        TenshisJeiLog.info("Registered AE2 wireless bookmark pull handler for JEI (unofficial).");
    }
}
