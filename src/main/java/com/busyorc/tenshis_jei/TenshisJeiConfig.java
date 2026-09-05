package com.busyorc.tenshis_jei;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Configuration for Tenshi's JEI Addon; saved as config/tenshis_jei_addon.toml.
 */
public final class TenshisJeiConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue DEBUG = BUILDER
        .comment(
            "Enable debug logging. When false (default), the mod writes no log lines at all.",
            "启用调试日志。默认 false：关闭时不输出任何日志，避免污染日志。"
        )
        .define("debug", false);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private TenshisJeiConfig() {
    }
}
