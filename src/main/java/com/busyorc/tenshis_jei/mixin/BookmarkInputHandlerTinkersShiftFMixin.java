package com.busyorc.tenshis_jei.mixin;

import com.busyorc.tenshis_jei.TenshisJeiLog;
import com.busyorc.tenshis_jei.compat.tinkers.TinkersCompatBridge;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.input.InputModifiers;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.input.handlers.BookmarkInputHandler;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

/**
 * 收藏栏键盘联动（挂在 fork 的 BookmarkInputHandler 输入入口 HEAD）：
 * shift+F：悬停物品是匠魂工具时，为每个额外 modifier 的原材料建配方树收藏栏组。
 * V / shift+V 的 ME 无线拉取【不使用自定义拦截】：由 fork 原生的 handleBookmarkPull
 * 处理，本模组只注册 BookmarkExternalStorageSnapshots.Provider 提供无线网络的
 * 外部存储快照（见 compat/ae2/WirelessExternalStorageSnapshotProvider），
 * 规划/发包/服务端抽取全部走 JEI fork 自身逻辑。
 */
@Mixin(value = BookmarkInputHandler.class, remap = false)
public abstract class BookmarkInputHandlerTinkersShiftFMixin {

    @Shadow(remap = false)
    @Final
    private BookmarkOverlay bookmarkOverlay;

    @Shadow(remap = false)
    @Final
    private BookmarkList bookmarkList;

    @Shadow(remap = false)
    @Final
    private IIngredientManager ingredientManager;

    @Inject(remap = false,
        method = "handleUserInput(Lnet/minecraft/client/gui/screens/Screen;Lmezz/jei/gui/input/UserInput;Lmezz/jei/common/input/IInternalKeyMappings;)Ljava/util/Optional;",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void tenshisJei$shiftF(
        Screen screen,
        UserInput input,
        IInternalKeyMappings keyBindings,
        CallbackInfoReturnable<Optional<IUserInputHandler>> cir
    ) {
        boolean isShiftF = InputModifiers.hasShift(input.getModifiers())
            && !InputModifiers.hasControl(input.getModifiers())
            && !InputModifiers.hasAlt(input.getModifiers())
            && keyBindings.getFavoriteRecipe().matchesIgnoringModifiers(input.getKey());
        if (!isShiftF) {
            return;
        }
        Optional<IUserInputHandler> handled = handleTinkersShiftF(input);
        if (handled.isPresent()) {
            cir.setReturnValue(handled);
        }
    }

    // ---------------- shift+F：匠魂额外 modifier 的原材料配方树 ----------------

    private Optional<IUserInputHandler> handleTinkersShiftF(UserInput input) {
        if (!this.bookmarkOverlay.isMouseOver(input.getMouseX(), input.getMouseY())) {
            return Optional.empty();
        }
        ItemStack toolStack = getHoveredItemStack(input);
        if (toolStack.isEmpty() || !TinkersCompatBridge.isTinkersTool(toolStack)) {
            TenshisJeiLog.info("[ET-jei] shift+F on bookmark bar: not a tinkers tool -> skip");
            return Optional.empty(); // 判定 1：不是匠魂工具 -> 跳过，无事发生
        }
        TenshisJeiLog.info("[ET-jei] shift+F on bookmark bar: tinkers tool detected -> building modifier trees");
        if (input.isSimulate()) {
            return Optional.of((IUserInputHandler) (Object) this);
        }
        List<String> groupIds = TinkersCompatBridge.buildModifierRecipeTrees(toolStack, this.bookmarkList, this.ingredientManager);
        if (groupIds.isEmpty()) {
            TenshisJeiLog.info("[ET-jei] shift+F: no extra modifiers with recipe materials -> nothing built");
            return Optional.empty(); // 判定 2/3：无额外 modifier 或配方为空 -> 无事发生
        }
        TenshisJeiLog.info("[ET-jei] shift+F: built " + groupIds.size() + " modifier recipe tree group(s) in bookmark bar");
        this.bookmarkOverlay.showBookmarkPanel();
        return Optional.of((IUserInputHandler) (Object) this);
    }

    private ItemStack getHoveredItemStack(UserInput input) {
        try {
            Optional<IElement<?>> element = this.bookmarkOverlay
                .getIngredientUnderMouse(input.getMouseX(), input.getMouseY())
                .findFirst()
                .map(clickable -> (IElement<?>) (Object) clickable.getElement());
            if (element.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ITypedIngredient<?> typed = element.get().getTypedIngredient();
            if (typed == null) {
                return ItemStack.EMPTY;
            }
            if (typed.getIngredient() instanceof ItemStack stack) {
                return stack;
            }
            return ItemStack.EMPTY;
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }
}
