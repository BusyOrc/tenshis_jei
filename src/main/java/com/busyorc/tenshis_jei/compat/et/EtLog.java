package com.busyorc.tenshis_jei.compat.et;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ET 联动专用日志：**不受 debug 配置限制，始终输出**。
 * 目的：ET 联动问题需要真实日志定位，而模组默认 debug=false 时所有 TenshisJeiLog 都是空操作，
 * 导致日志里看不到任何线索。这里只用于 ET 联动的关键节点（注册/客户端 provider/填充/合成），
 * 输出量很小。
 */
public final class EtLog {
    private static final Logger LOGGER = LoggerFactory.getLogger("ET-jei");

    private EtLog() {
    }

    public static void info(String message, Object... args) {
        LOGGER.info(message, args);
    }

    public static void warn(String message, Object... args) {
        LOGGER.warn(message, args);
    }
}
