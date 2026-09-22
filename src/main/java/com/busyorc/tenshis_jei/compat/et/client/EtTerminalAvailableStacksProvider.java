package com.busyorc.tenshis_jei.compat.et.client;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.common.IClientRepo;
import com.busyorc.tenshis_jei.compat.et.EtLog;
import me.myogoo.extendedterminal.menu.extendedterminal.ETTerminalMenu;
import mezz.jei.gui.bookmarks.chain.BookmarkExternalStorageSnapshots;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAvailableStacksProviders;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 把"当前打开的 ET 终端联网内容"提供给 JEIU 的收藏组/配方树自动合成材料计算。
 * <p>
 * fork 自带的 Ae2AvailableStacksProvider 只认 CraftingTermMenu，而 ET 终端是
 * ETTerminalBaseMenu extends MEStorageMenu（不是 CraftingTermMenu），所以必须由本类补上。
 * <p>
 * 关键：fork 的快照读取（BookmarkExternalStorageSnapshots.readEntries）在自动合成任务进行中
 * 会被 {@code BookmarkCraftingScope.interests} 过滤——只返回"兴趣物品"，条目数会骤降到个位数
 * （日志里 1~3 条），导致 fork 的 canCraft 判断材料不足而**根本不发包**（切石/锻造大量因此失败）。
 * 因此这里**直接读终端自己的客户端仓库 IClientRepo（全量，不受 scope 影响）**，只在拿不到仓库时
 * 才回退到 fork 的作用域快照。
 */
public class EtTerminalAvailableStacksProvider implements BookmarkAvailableStacksProviders.Provider {
    /** 日志里最多列出多少个条目（避免刷屏）。 */
    private static final int LOG_LIMIT = 8;

    @Override
    public Optional<List<ItemStack>> getAvailableStacks(AbstractContainerMenu menu) {
        if (!(menu instanceof ETTerminalMenu et)) {
            return Optional.empty();
        }
        Optional<List<ItemStack>> full = readFullClientRepo(et);
        if (full.isPresent() && !full.get().isEmpty()) {
            log("full", full.get());
            return full;
        }
        // 回退：fork 的作用域快照（AE2 快照 provider 处理 MEStorageMenu）
        Optional<List<ItemStack>> scoped = BookmarkExternalStorageSnapshots.readEntries(menu)
            .map(BookmarkExternalStorageSnapshots::toAvailableStacks);
        log(full.isPresent() ? "repo-empty->scoped" : "no-repo->scoped", scoped.orElse(List.of()));
        return scoped;
    }

    /** 直接读 ET 终端客户端的 ME 仓库（全量）。仓库尚未同步时返回 empty 由调用方回退。 */
    private static Optional<List<ItemStack>> readFullClientRepo(ETTerminalMenu et) {
        IClientRepo repo;
        try {
            repo = et.getClientRepo();
        } catch (Throwable t) {
            return Optional.empty();
        }
        if (repo == null) {
            return Optional.empty();
        }
        List<ItemStack> stacks = new ArrayList<>();
        try {
            for (GridInventoryEntry entry : repo.getAllEntries()) {
                AEKey what = entry.getWhat();
                if (!(what instanceof AEItemKey itemKey)) {
                    continue;
                }
                long amount = entry.getStoredAmount();
                if (amount <= 0) {
                    continue;
                }
                ItemStack stack = itemKey.toStack((int) Math.min(amount, Integer.MAX_VALUE));
                if (!stack.isEmpty()) {
                    stacks.add(stack);
                }
            }
        } catch (Throwable t) {
            return Optional.empty();
        }
        return Optional.of(stacks);
    }

    private static void log(String source, List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            EtLog.info("[ET-jei] availableStacksProvider(ET)/{} -> 0 entries", source);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stacks.size() && i < LOG_LIMIT; i++) {
            ItemStack stack = stacks.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(stack.getCount()).append("x ").append(stack.getItem());
        }
        if (stacks.size() > LOG_LIMIT) {
            sb.append(", ...(+").append(stacks.size() - LOG_LIMIT).append(")");
        }
        EtLog.info("[ET-jei] availableStacksProvider(ET)/{} -> {} entries: {}", source, stacks.size(), sb);
    }
}
