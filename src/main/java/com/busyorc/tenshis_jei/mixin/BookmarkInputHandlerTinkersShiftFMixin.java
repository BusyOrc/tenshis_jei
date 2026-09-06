package com.busyorc.tenshis_jei.mixin;

import com.busyorc.tenshis_jei.TenshisJeiLog;
import com.busyorc.tenshis_jei.client.TenshisJeiKeys;
import com.busyorc.tenshis_jei.compat.etstlib.EtstLibCompatBridge;
import com.busyorc.tenshis_jei.compat.tinkers.AnvilAutoReSlot;
import com.busyorc.tenshis_jei.compat.tinkers.TinkersCompatBridge;
import java.util.UUID;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.network.IConnectionToServer;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingBridge;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingRunner;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkGhostOverlayTargetSlots;
import mezz.jei.gui.bookmarks.hotkeys.ClientCraftingGridClickRunner;
import mezz.jei.gui.input.InputModifiers;
import mezz.jei.gui.overlay.bookmarks.PlayerInventoryRecipeChainTooltipInventoryProvider;
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

    @Shadow(remap = false)
    @Final
    private IConnectionToServer serverConnection;

    @Shadow(remap = false)
    @Final
    private BookmarkAutoCraftingRunner autoCraftingRunner;

    @Shadow(remap = false)
    @Final
    private ClientCraftingGridClickRunner clientCraftingGridClickRunner;

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
        boolean shiftOnly = InputModifiers.hasShift(input.getModifiers())
            && !InputModifiers.hasControl(input.getModifiers())
            && !InputModifiers.hasAlt(input.getModifiers());
        boolean isCraftKey = shiftOnly && keyBindings.getCraftItems().matchesIgnoringModifiers(input.getKey());
        // shift+C（JEI 默认合成键）：砧 GUI 打开且 slot0 有工具时触发自动强化
        if (isCraftKey && isTinkerAnvilOpen()) {
            Optional<IUserInputHandler> handled = handleAnvilShiftF(input);
            if (handled.isPresent()) {
                cir.setReturnValue(handled);
            }
            return;
        }
        // shift+F（本模组可改键位，默认 F，触发仍需 shift）：悬停收藏栏里的匠魂工具时生成强化配方链
        boolean genKey = shiftOnly && input.is(TenshisJeiKeys.GEN_MODIFIER_CHAIN);
        if (genKey) {
            Optional<IUserInputHandler> handled = handleTinkersShiftF(input);
            if (handled.isPresent()) {
                cir.setReturnValue(handled);
            }
        }
    }

    /** 当前是否打开砧/匠工台菜单且 slot0 有工具（自动强化的前提）。 */
    private static boolean isTinkerAnvilOpen() {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs)) {
                return false;
            }
            net.minecraft.world.inventory.AbstractContainerMenu menu = cs.getMenu();
            boolean tinker = menu instanceof slimeknights.tconstruct.tables.menu.TinkerStationContainerMenu
                || menu instanceof slimeknights.tconstruct.tables.menu.CraftingStationContainerMenu;
            return tinker && !menu.slots.isEmpty() && !menu.getSlot(0).getItem().isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    // ---------------- shift+F：匠魂额外 modifier 的原材料配方树 ----------------

    private Optional<IUserInputHandler> handleTinkersShiftF(UserInput input) {
        if (!this.bookmarkOverlay.isMouseOver(input.getMouseX(), input.getMouseY())) {
            return Optional.empty(); // 该键只做"悬停收藏工具 -> 生成配方链"，不做砧自动强化（砧用 shift+C）
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

    // ---------------- shift+F：工匠站/砧 打开时（slot0 工具）----------------
    private Optional<IUserInputHandler> handleAnvilShiftF(UserInput input) {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs)) {
                return Optional.empty();
            }
            net.minecraft.world.inventory.AbstractContainerMenu menu = cs.getMenu();
            boolean isTinker = menu instanceof slimeknights.tconstruct.tables.menu.TinkerStationContainerMenu
                || menu instanceof slimeknights.tconstruct.tables.menu.CraftingStationContainerMenu;
            if (!isTinker) {
                return Optional.empty();
            }
            if (menu.getSlot(0).getItem().isEmpty()) {
                TenshisJeiLog.info("[ET-jei] anvil-shiftF: tinker menu open but slot0 empty -> skip");
                return Optional.of((IUserInputHandler) (Object) this);
            }
            net.minecraft.world.item.ItemStack tool = menu.getSlot(0).getItem();
            TenshisJeiLog.info("[ET-jei] anvil-shiftF: menu=" + menu.getClass().getSimpleName() + " containerId=" + menu.containerId
                + " tool=" + tool + " toolCount=" + tool.getCount());
            var uuid = com.busyorc.tenshis_jei.compat.etstlib.EtstLibCompatBridge.getToolUuid(tool);
            TenshisJeiLog.info("[ET-jei] anvil-shiftF: toolUuid=" + (uuid.isPresent() ? uuid.get() : "<empty>")
                + " etstlibLoaded=" + com.busyorc.tenshis_jei.compat.etstlib.EtstLibCompatBridge.isEtstLibLoaded());
            // 鼠标悬停的收藏组
            Optional<String> groupId = Optional.empty();
            try {
                groupId = this.bookmarkOverlay.getPullGroupIdUnderMouse(input.getMouseX(), input.getMouseY());
            } catch (Throwable t) {
                groupId = Optional.empty();
            }
            TenshisJeiLog.info("[ET-jei] anvil-shiftF: hoveredGroup=" + groupId.orElse("<none>") + " craftingMode="
                + groupId.map(g -> String.valueOf(this.bookmarkList.isGroupCraftingMode(g))).orElse("-"));
            if (groupId.isPresent()) {
                try {
                    int chainSize = this.bookmarkList.getRecipeChainInputs(groupId.get()).size();
                    TenshisJeiLog.info("[ET-jei] anvil-shiftF: chainInputs=" + chainSize
                        + " collapsed=" + this.bookmarkList.getCollapsedRecipeIds(groupId.get()).size());
                } catch (Throwable t) {
                    TenshisJeiLog.info("[ET-jei] anvil-shiftF: chain read threw: " + t);
                }
            }
            // ---- 真实执行（复用 fork 的 auto-crafting 管道，按悬停合成组整链跑）----
            String hg = groupId.orElse(null);
            boolean craftingMode = hg != null && this.bookmarkList.isGroupCraftingMode(hg);
            boolean toolOk = uuid.isPresent();
            if (input.isSimulate()) {
                TenshisJeiLog.info("[ET-jei] anvil-shiftF[simulate] craftable=" + (toolOk && craftingMode));
                return Optional.of((IUserInputHandler) (Object) this);
            }
            if (!toolOk || !craftingMode) {
                TenshisJeiLog.info("[ET-jei] anvil-shiftF: NOT running (uuid=" + toolOk + " craftingGroup=" + craftingMode + ")");
                return Optional.of((IUserInputHandler) (Object) this);
            }
            boolean started = runAnvilGroup(hg, menu, mc, uuid);
            TenshisJeiLog.info("[ET-jei] anvil-shiftF: fork auto-craft task started=" + started);
        } catch (Throwable t) {
            TenshisJeiLog.info("[ET-jei] anvil-shiftF threw: " + t);
        }
        return Optional.of((IUserInputHandler) (Object) this);
    }

    /**
     * 复用 fork 的 BookmarkAutoCraftingRunner：为悬停的合成模式收藏组在当前工匠站/砧菜单上
     * 建立并启动整条配方链任务。fork 的 TinkerCraftingGridCraftExecutor 每次强化会把工具 shift
     * 出结果槽进物品栏（slot0 清空）；故每步都需把工具按 UUID 放回 slot0，才能连续施加下一 modifier
     * —— 由 AnvilAutoReSlot 客户端 tick 会话在 fork Task 运行期间自动回槽。
     */
    private boolean runAnvilGroup(String groupId, AbstractContainerMenu menu, net.minecraft.client.Minecraft mc, Optional<UUID> toolUuid) {
        try {
            int targetSlotCount = BookmarkGhostOverlayTargetSlots.fromMenu(menu).size();
            if (targetSlotCount <= 0) {
                TenshisJeiLog.info("[ET-jei] runAnvilGroup: targetSlotCount=" + targetSlotCount + " -> skip");
                return false;
            }
            PlayerInventoryRecipeChainTooltipInventoryProvider ip =
                new PlayerInventoryRecipeChainTooltipInventoryProvider(mc, this.ingredientManager);
            // shift+F 语义 = 跑完整条链
            boolean craftAll = true;
            if (this.serverConnection.isJeiOnServer()) {
                boolean started = BookmarkAutoCraftingBridge.createTask(
                        this.bookmarkList.getRecipeChainInputs(groupId),
                        this.bookmarkList.getCollapsedRecipeIds(groupId),
                        targetSlotCount,
                        menu.containerId,
                        () -> getAnvilInventoryInputs(groupId, ip),
                        ip::getAvailableStacks,
                        recipeUid -> this.bookmarkList.createRecipeLayoutDrawable(groupId, recipeUid),
                        this.serverConnection::sendPacketToServer,
                        () -> net.minecraft.client.Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> activeScreen
                            && activeScreen.getMenu() == menu,
                        craftAll
                    )
                    .map(this.autoCraftingRunner::start)
                    .orElse(false);
                TenshisJeiLog.info("[ET-jei] runAnvilGroup: server-path started=" + started);
                if (started && toolUuid.isPresent()) {
                    AnvilAutoReSlot.begin(toolUuid.get(), this.autoCraftingRunner);
                }
                return started;
            }
            // JEI 不在服务端 -> 纯客户端兜底（仍需把产物放回槽位，由客户端 tick 观察器处理）
            boolean started = BookmarkAutoCraftingBridge.createClientFallbackTask(
                    this.bookmarkList.getRecipeChainInputs(groupId),
                    this.bookmarkList.getCollapsedRecipeIds(groupId),
                    targetSlotCount,
                    menu,
                    () -> getAnvilInventoryInputs(groupId, ip),
                    ip::getAvailableStacks,
                    recipeUid -> this.bookmarkList.createRecipeLayoutDrawable(groupId, recipeUid),
                    this.clientCraftingGridClickRunner,
                    this.clientCraftingGridClickRunner::consumeLastResult,
                    () -> net.minecraft.client.Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> activeScreen
                        && activeScreen.getMenu() == menu,
                    craftAll
                )
                .map(this.autoCraftingRunner::start)
                .orElse(false);
            TenshisJeiLog.info("[ET-jei] runAnvilGroup: client-fallback started=" + started);
            if (started && toolUuid.isPresent()) {
                AnvilAutoReSlot.begin(toolUuid.get(), this.autoCraftingRunner);
            }
            return started;
        } catch (Throwable t) {
            TenshisJeiLog.info("[ET-jei] runAnvilGroup threw: " + t);
            return false;
        }
    }

    private List<mezz.jei.gui.bookmarks.chain.RecipeChainInput> getAnvilInventoryInputs(
        String groupId, PlayerInventoryRecipeChainTooltipInventoryProvider inventoryProvider
    ) {
        return List.copyOf(inventoryProvider.getInventoryInputs(groupId, -1));
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
