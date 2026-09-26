package com.busyorc.tenshis_jei.mixin;

import com.busyorc.tenshis_jei.compat.et.EtLog;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * WCWT 的终端菜单是 {@code WirelessComprehensiveWorkTerminalMenu extends CraftingTermMenu}，
 * 因此 fork 自带的 AE2 执行器（canHandle = menu instanceof CraftingTermMenu）会**先于**本模组的
 * WCWT 执行器把它接管，并按"工作台 3x3 网格"填料 —— 锻造/切石配方于是把材料全填进工作台区域、
 * 完全合不出来。这里让 fork 的 AE2 执行器对 WCWT 终端返回 false，把处理权交回本模组的
 * WcwtCraftingGridCraftExecutor（它会按配方类型自动切换手动工作区模式）。
 * <p>
 * 用类名遍历判断，**不直接引用 WCWT 类**，避免 WCWT 未安装时影响该 mixin。
 */
@Mixin(value = mezz.jei.neoforge.compat.ae2.Ae2CraftingGridCraftExecutor.class, remap = false)
public abstract class Ae2CraftingGridCraftExecutorWcwtSkipMixin {

    @Inject(method = "canHandle", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void tenshisJei$skipWcwtTerminal(AbstractContainerMenu menu, CallbackInfoReturnable<Boolean> cir) {
        if (menu != null && isWcwtTerminal(menu)) {
            EtLog.info("[ET-jei] WCWT 终端交给本模组执行器处理（跳过 JEIU 的 AE2 执行器）");
            cir.setReturnValue(false);
        }
    }

    private static boolean isWcwtTerminal(Object menu) {
        try {
            if (!net.neoforged.fml.ModList.get().isLoaded("wcwt")) {
                return false;
            }
        } catch (Throwable t) {
            return false;
        }
        String target = "com.lhy.wcwt.menu.WirelessComprehensiveWorkTerminalMenu";
        for (Class<?> c = menu.getClass(); c != null; c = c.getSuperclass()) {
            if (target.equals(c.getName())) {
                return true;
            }
        }
        return false;
    }
}
