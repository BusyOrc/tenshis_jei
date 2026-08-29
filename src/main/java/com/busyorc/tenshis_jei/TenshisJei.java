package com.busyorc.tenshis_jei;

import com.busyorc.tenshis_jei.compat.et.EtTerminalCraftingGridCraftExecutor;
import mezz.jei.common.bookmarks.CraftingGridCraftExecutors;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;

/**
 * Tenshi's JEI — 基础模组（已加入 ExtendedTerminal 终端 × JEI(unofficial fork) 自动合成兼容）。
 * 前置 = JEI（unofficial fork，mod id "jei"）+ Extended Terminal（mod id "extendedterminal"），
 * 见 resources/META-INF/neoforge.mods.toml。
 * <p>
 * 本类构造器注册服务端自动合成 executor（复制 fork 自身的注册模式）；
 * 客户端侧的两个 provider 由 compat/et/client/EtJeiClientCompat 注册。
 */
@Mod(TenshisJei.MOD_ID)
public class TenshisJei {

    public static final String MOD_ID = "tenshis_jei";
    public static final String MOD_NAME = "Tenshi's JEI";
    public static final String VERSION = "1.0.0";

    public TenshisJei(IEventBus modEventBus, ModContainer modContainer) {
        // Config file: config/tenshis_jei.toml (debug switch, default off; filename = mod id).
        modContainer.registerConfig(ModConfig.Type.COMMON, TenshisJeiConfig.SPEC, TenshisJei.MOD_ID + ".toml");
        // 注册"在 ET 终端内按配方树自动合成"的服务端执行器到 JEI fork 的注册表。
        CraftingGridCraftExecutors.registerExecutor(new EtTerminalCraftingGridCraftExecutor());
        TenshisJeiLog.info("Registered ExtendedTerminal terminal crafting-grid executor for JEI (unofficial).");
    }
}
