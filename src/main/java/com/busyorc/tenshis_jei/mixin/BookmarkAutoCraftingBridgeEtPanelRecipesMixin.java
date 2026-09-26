package com.busyorc.tenshis_jei.mixin;

import com.busyorc.tenshis_jei.compat.et.EtLog;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.myogoo.extendedterminal.menu.extendedterminal.ETTerminalMenu;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * JEIU 19.54 在 BookmarkAutoCraftingBridge.craft(...) 开头加了一道硬性守卫：
 * <pre>
 *   if (layout == null || !RecipeTypes.CRAFTING.getUid().equals(layout.getRecipeCategory().getRecipeType().getUid()))
 *       return false;   // 只允许"工作台(crafting)"类别的配方发包
 * </pre>
 * 于是收藏组/配方树里只要有切石机(stonecutting)/锻造台(smithing)配方，客户端在发包前就被拒掉
 * （日志表现：diag 显示 layout=true/fill ok/canCraft=true，但 craft() returned=false，服务端永远收不到包）。
 * <p>
 * 本模组的 ET 终端执行器在**服务端**本就支持这两类配方（按 recipeId 路由到面板流程并自动切换 ET 模式），
 * 所以这里只针对"当前打开的是 ET 终端 + 配方确实是切石/锻造"的情况放行该守卫，其它情况保持 JEIU 原行为。
 */
@Mixin(value = mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingBridge.class, remap = false)
public abstract class BookmarkAutoCraftingBridgeEtPanelRecipesMixin {

    @ModifyExpressionValue(
        method = "craft(Lnet/minecraft/resources/ResourceLocation;IIILjava/util/function/Supplier;Ljava/util/function/Function;Ljava/util/function/Consumer;ZIILjava/util/concurrent/atomic/AtomicBoolean;Ljava/lang/Runnable;)Z",
        at = @At(
            value = "INVOKE",
            target = "Lmezz/jei/api/recipe/RecipeType;getUid()Lnet/minecraft/resources/ResourceLocation;",
            ordinal = 0
        ),
        require = 0,
        remap = false
    )
    private static ResourceLocation tenshisJei$allowEtPanelRecipes(
        ResourceLocation original,
        ResourceLocation recipeUid,
        int multiplier,
        int targetSlotCount,
        int containerId,
        Supplier<List<ItemStack>> availableStacksSupplier,
        Function<ResourceLocation, Optional<IRecipeLayoutDrawable<?>>> recipeLayoutResolver,
        Consumer<?> packetSender,
        boolean simulate,
        int taskId,
        int requestId,
        AtomicBoolean craftedOnePacket,
        Runnable afterCraftAccepted
    ) {
        try {
            // original 即 RecipeTypes.CRAFTING.getUid()，只在原判定以它为基准时才接管
            if (!RecipeTypes.CRAFTING.getUid().equals(original)) {
                return original;
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.level == null) {
                return original;
            }
            AbstractContainerMenu menu = minecraft.player.containerMenu;
            boolean etTerminal = menu instanceof ETTerminalMenu;
            boolean wcwtTerminal = isWcwtTerminal(menu);
            if (!etTerminal && !wcwtTerminal) {
                return original;
            }
            // 该配方在服务端的真实类型必须是我们对应终端执行器支持的类型：
            // ET 终端：切石 + 锻造；WCWT 终端：只有锻造（该终端没有手动切石面板）
            RecipeHolder<?> holder = minecraft.level.getRecipeManager().byKey(recipeUid).orElse(null);
            if (holder == null) {
                return original;
            }
            RecipeType<?> type = holder.value().getType();
            boolean supported = type == RecipeType.SMITHING
                || (type == RecipeType.STONECUTTING && etTerminal);
            if (!supported) {
                return original;
            }
            IRecipeLayoutDrawable<?> layout = recipeLayoutResolver.apply(recipeUid).orElse(null);
            if (layout == null) {
                return original;
            }
            ResourceLocation categoryUid = layout.getRecipeCategory().getRecipeType().getUid();
            EtLog.info("[ET-jei] 放宽 JEIU 守卫: 在 {} 终端内放行 {} (serverType={}, jeiCategory={})",
                etTerminal ? "ET" : "WCWT", recipeUid, type, categoryUid);
            return categoryUid; // 使 fork 的 "CRAFTING.equals(...)" 比较成立，从而放行发包
        } catch (Throwable t) {
            EtLog.info("[ET-jei] 放宽守卫时异常，保持原行为: " + t);
            return original;
        }
    }

    /**
     * 用类名遍历判断是否为 WCWT 终端菜单——**不直接引用 WCWT 类**，
     * 这样 WCWT 未安装时也不会因类加载失败而影响本 mixin。
     */
    private static boolean isWcwtTerminal(Object menu) {
        if (menu == null) {
            return false;
        }
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
