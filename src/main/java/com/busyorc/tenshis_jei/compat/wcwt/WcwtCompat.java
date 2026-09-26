package com.busyorc.tenshis_jei.compat.wcwt;

import net.neoforged.fml.ModList;

/**
 * WCWT (AE2 Wireless Comprehensive Work Terminal) 可选前置桥。
 * 本类不引用任何 WCWT 类；真正的实现（直接引用 WCWT 类型）在 {@link WcwtCompatImpl}，
 * 只有 WCWT 已加载时才会被加载/调用，避免未装该模组时出现 NoClassDefFoundError。
 */
public final class WcwtCompat {
    public static final String MOD_ID = "wcwt";

    private WcwtCompat() {
    }

    public static boolean isLoaded() {
        try {
            return ModList.get().isLoaded(MOD_ID);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 服务端：注册 WCWT 终端的 craft / fill 执行器。 */
    public static void registerServer() {
        if (!isLoaded()) {
            return;
        }
        WcwtCompatImpl.registerServer();
    }

    /** 客户端：注册 WCWT 终端的目标槽位 provider 等。 */
    public static void registerClient() {
        if (!isLoaded()) {
            return;
        }
        WcwtCompatImpl.registerClient();
    }
}
