package com.busyorc.tenshis_jei.compat.curios;

import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.locator.ItemMenuHostLocator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

/**
 * 适配 Curios 槽位的 AE2 MenuLocator（与 ExtendedAE_Plus 的 CuriosItemLocator 同思路）：
 * 通过 slotId + index 在两端定位 Curios 槽位中的实际物品，使无线终端的
 * 栈解析（ItemMenuHost.getItemStack -> locator.locateItem）与 NBT 变更（耗电）能持久化。
 */
public record CuriosItemLocator(String slotId, int index) implements ItemMenuHostLocator {

    @Override
    @Nullable
    public <T> T locate(Player player, Class<T> hostInterface) {
        ItemStack stack = locateItem(player);
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.getItem() instanceof WirelessTerminalItem wirelessTerminal) {
            ItemMenuHost host = wirelessTerminal.getMenuHost(player, this, null);
            if (host != null && hostInterface.isInstance(host)) {
                return hostInterface.cast(host);
            }
        }
        return null;
    }

    @Override
    public ItemStack locateItem(Player player) {
        try {
            var opt = CuriosApi.getCuriosInventory(player);
            if (opt.isPresent()) {
                ICuriosItemHandler handler = opt.get();
                ICurioStacksHandler stacksHandler = handler.getCurios().get(slotId);
                if (stacksHandler != null) {
                    return stacksHandler.getStacks().getStackInSlot(index);
                }
            }
        } catch (Throwable ignored) {
        }
        return ItemStack.EMPTY;
    }

    @Override
    public @Nullable BlockHitResult hitResult() {
        return null;
    }

    public void writeToPacket(FriendlyByteBuf buf) {
        buf.writeUtf(slotId);
        buf.writeVarInt(index);
    }

    public static CuriosItemLocator readFromPacket(FriendlyByteBuf buf) {
        String slotId = buf.readUtf();
        int index = buf.readVarInt();
        return new CuriosItemLocator(slotId, index);
    }
}
