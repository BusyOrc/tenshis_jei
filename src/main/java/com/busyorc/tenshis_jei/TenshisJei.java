package com.busyorc.tenshis_jei;

import com.busyorc.tenshis_jei.compat.ae2.WirelessBookmarkPullTransferHandler;
import com.busyorc.tenshis_jei.compat.et.EtTerminalCraftingGridCraftExecutor;
import mezz.jei.common.bookmarks.CraftingGridCraftExecutors;
import mezz.jei.common.bookmarks.ServerBookmarkPullTransfers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;

/**
 * Tenshi's JEI Addon — 基础模组（已加入 ExtendedTerminal 终端 × JEI(unofficial fork) 自动合成兼容）。
 * 前置 = JEI（unofficial fork，mod id "jei"）+ Extended Terminal（mod id "extendedterminal"），
 * 见 resources/META-INF/neoforge.mods.toml。
 * <p>
 * 本类构造器注册服务端自动合成 executor（复制 fork 自身的注册模式）；
 * 客户端侧的两个 provider 由 compat/et/client/EtJeiClientCompat 注册。
 */
@Mod(TenshisJei.MOD_ID)
public class TenshisJei {

    public static final String MOD_ID = "tenshis_jei_addon";
    public static final String MOD_NAME = "Tenshi's JEI Addon";
    public static final String VERSION = "1.0.0";

    public TenshisJei(IEventBus modEventBus, ModContainer modContainer) {
        // Config file: config/tenshis_jei_addon.toml (debug switch, default off; filename = mod id).
        modContainer.registerConfig(ModConfig.Type.COMMON, TenshisJeiConfig.SPEC, TenshisJei.MOD_ID + ".toml");
        // 注册"在 ET 终端内按配方树自动合成"的服务端执行器到 JEI fork 的注册表。
        CraftingGridCraftExecutors.registerExecutor(new EtTerminalCraftingGridCraftExecutor());
        // V/shift+V 拉取配方树物品时，若有 AE2 无线终端在身（背包或 Curios 饰品槽），
        // 可不打开 ME 终端直接从网络拉取。
        ServerBookmarkPullTransfers.registerHandler(new WirelessBookmarkPullTransferHandler());
        // 注册 Curios 槽位定位器（可选前置；未加载 Curios 时注册本身无害）
        try {
            appeng.menu.locator.MenuLocators.register(
                com.busyorc.tenshis_jei.compat.curios.CuriosItemLocator.class,
                com.busyorc.tenshis_jei.compat.curios.CuriosItemLocator::writeToPacket,
                com.busyorc.tenshis_jei.compat.curios.CuriosItemLocator::readFromPacket
            );
        } catch (RuntimeException e) {
            TenshisJeiLog.info("CuriosItemLocator registration failed: " + e);
        }
        // 客户端：把"无线终端已连网络"注册为 JEI fork 的外部存储快照提供器，
        // V/shift+V 拉取完全走 fork 自己的 handleBookmarkPull（规划/发包/服务端抽取）。
        if (net.neoforged.fml.loading.FMLLoader.getDist().isClient()) {
            try {
                mezz.jei.gui.bookmarks.chain.BookmarkExternalStorageSnapshots.registerProvider(
                    new com.busyorc.tenshis_jei.compat.ae2.WirelessExternalStorageSnapshotProvider()
                );
                TenshisJeiLog.info("Registered wireless external-storage snapshot provider for JEI bookmark pull.");
            } catch (RuntimeException e) {
                TenshisJeiLog.info("Wireless snapshot provider registration failed: " + e);
            }
        }
        // 无线网络快照同步：客户端定时请求服务端枚举（仅客户端有意义，注册无害）
        modEventBus.addListener(com.busyorc.tenshis_jei.network.WirelessPayloadRegistrar::registerPayloads);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.busyorc.tenshis_jei.network.WirelessPayloadRegistrar::onClientTick);
        TenshisJeiLog.info("Registered ExtendedTerminal crafting executor + AE2 wireless bookmark pull handler for JEI (unofficial).");
    }
}
