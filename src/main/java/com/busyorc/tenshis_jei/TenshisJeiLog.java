package com.busyorc.tenshis_jei;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gatekeeper for all log output of this mod. With "debug" disabled in the
 * config, every call is a no-op so the mod never pollutes the game log.
 */
public final class TenshisJeiLog {
    private static final Logger LOGGER = LoggerFactory.getLogger(TenshisJei.MOD_NAME);

    private TenshisJeiLog() {
    }

    public static boolean isDebugEnabled() {
        // Defensive read: if the config file has not finished loading yet (e.g.
        // during the mod constructor) or failed to load, we treat debug as off
        // instead of throwing IllegalStateException like the stack in a crash
        // report can show for other mods.
        try {
            return TenshisJeiConfig.DEBUG.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public static void info(String message, Object... args) {
        if (isDebugEnabled()) {
            LOGGER.info(message, args);
        }
    }

    public static void warn(String message, Object... args) {
        if (isDebugEnabled()) {
            LOGGER.warn(message, args);
        }
    }
}
