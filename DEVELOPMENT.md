# Tenshi's JEI Addon（tenshis_jei_addon）开发文档

NeoForge 1.21.1 的 JEI 分支增强模组，基于 JEIunofficial(fork) 19.44.0.403（mod id `jei`）。
本模组不改 fork 本体，全部通过 mixin + 服务端处理器注册 + 网络 payload 同步 + 可选前置桥接实现。

---

## 1. 功能总览

| 功能 | 触发 | 说明 | 状态 |
| --- | --- | --- | --- |
| 匠魂配方树 | 收藏栏上 shift+F（悬停匠魂工具） | 每个额外 modifier 的配方建一组，组内容为该配方的原材料（同一配方放一组，不再链工作台/砂铸等配方，忽略 JEI tag 配方） | ✅ 已验证 |
| ME 无线拉取 | V / shift+V（悬停收藏栏配方树组或组面板槽、不开 ME 终端） | 走 JEI fork 原生拉取；网络快照由 payload 定时同步（服务端 EAEP 定位+枚举 → 客户端缓存 → Provider 喂 fork 规划 → 服务端 EAEP 抽取） | ✅ 已验证 |
| ET 终端配方批处理（既有） | 终端内合成 | CraftingTermSlot 批量插入/取回 等（第 11 轮已验收："这次正常了"） | ✅ |
| 链数学输入日志（既有） | 自动合成时 | RecipeChainMath 输入/输出日志（诊断用） | ✅ |

可选前置（缺省时对应功能自动降级或跳过）：
- tconstruct 3.12.x + mantle（匠魂功能）
- extendedae_plus（EAEP，无线拉取首选路径）+ ae2wtlib / ExtendedAE / architectury / cloth-config / Glodium
- curios（饰品槽无线终端检测）
- AE2 为硬依赖（ExtendedTerminal 依赖），可直接引用其类

---

## 2. 架构

### 2.1 包结构

`com.busyorc.tenshis_jei`
- `TenshisJei.java` — 模组入口。构造函数中注册：
  - 服务端 `ServerBookmarkPullTransfers.registerHandler(new WirelessBookmarkPullTransferHandler())`（排在 fork 处理器之后）
  - `MenuLocators.register(CuriosItemLocator.class, ...)`（Curios 定位器，供 AE2 菜单宿主解析）
  - 客户端（dist 守卫）`BookmarkExternalStorageSnapshots.registerProvider(new WirelessExternalStorageSnapshotProvider())`
  - 网络 payload（`RegisterPayloadHandlersEvent`）+ 客户端 tick 刷新（`NeoForge.EVENT_BUS`）
- `TenshisJeiLog.java` — 统一日志（日志前缀 `[Tenshi's JEI Addon/]`，调试行 `[ET-jei] ...`）
- `TenshisJeiConfig.java / RecipeTreeCraftingMode.java / TenshisJeiCraftingModes.java` — 既有配置与合成模式

`network/` — 无线网络快照同步（自定义 payload，通道 `tenshis_jei_addon`）
- `WirelessSnapshotRequestPayload`（C2S，空）— 客户端请求服务端枚举网络
- `WirelessSnapshotDataPayload`（S2C，`List<Entry(ItemStack, long)>`）— 服务端回传网络物品（代表栈 count=1 + 可用量）
- `WirelessSnapshotPayloadHandler` — 服务端：EAEP 定位+枚举（`Bridge.readNetworkEntries`）回包；客户端：写缓存
- `WirelessSnapshotCache` — 客户端缓存（volatile 列表）
- `WirelessPayloadRegistrar` — payload 注册 + 客户端 tick 刷新（每 32 tick、有屏幕打开时发一次请求）

`compat/et`（既有 ET 终端集成）、`compat/tinkers`、`compat/curios`、`compat/ae2`、`compat/eaep`
- 每个可选前置 = 一个 `*CompatBridge`（不引用前置类，仅 ModList 判定）+ `*CompatImpl`（直接引用前置类，仅在加载时被桥接类触发加载）
- 规则：桥接类永远可安全加载；实现类引用的前置类只在方法被调用时解析（JVM 惰性类加载）
- `compat/ae2/WirelessExternalStorageSnapshotProvider` — JEI fork 的外部存储快照提供器（纯读缓存，客户端）
- `compat/ae2/WirelessBookmarkPullTransferHandler` — 服务端拉取处理器（EAEP-first，EAEP 缺省时自研 AE2 直连降级）

`mixin/`
- `BookmarkInputHandlerTinkersShiftFMixin.java` — 挂 fork 的 `BookmarkInputHandler.handleUserInput` HEAD：**只做 shift+F 匠魂建树**；V/shift+V 拉取完全交回 fork（不拦截、不自定义规划）。
- `BookmarkOverlayScrollStepAreaShiftMixin.java` — 挂 `BookmarkOverlay.calculateScrollStepArea` HEAD：把底部步数量输入栏位（ScrollStepTextField 区域）**右移 20px**（复刻原公式 + 20，右边界锚定所以宽度减 20px）。
- `CraftingTermSlotBatchMixin.java / BookmarkAutoCraftingBridgeExactCraftingMixin / BookmarkAutoCraftingTaskDispatchExactMixin / RecipeChainMathInputLogMixin` — 既有 ET/合成 mixin

### 2.2 可选前置判定（重要）

任何**直接**引用可选模组类的代码只允许出现在对应 `CompatImpl`/locator 内；
入口类、mixin、提供器只能引用桥接类。例如 `EaepCompatImpl` 直接 import
`com.extendedae_plus.util.wireless.WirelessTerminalLocator`，而提供器与处理器只调
`EaepCompatBridge.isExtendedAEPlusLoaded()/getConnectedGrid()/readNetworkEntries()/pull(...)`。

---

## 3. 匠魂配方树（shift+F）

### 3.1 触发与判定链

1. fork 的 `BookmarkInputHandler.handleUserInput` HEAD 注入（mixin），判定：
   - shift + 非 ctrl/alt + `keyBindings.getFavoriteRecipe()` 匹配（shift+F）
   - 鼠标在收藏栏上（`bookmarkOverlay.isMouseOver`）
   - 悬停物品是匠魂工具（`slimeknights.tconstruct.common.TinkerTags.Items.MODIFIABLE`）
   - 是匠魂工具时 claim（simulate 阶段空返回 claim，执行阶段真正建树）
2. `TinkersCompatBridge.buildModifierRecipeTrees(stack, bookmarkList, ingredientManager)`
   → `TinkersToolCompatImpl`（仅 tconstruct 加载时被触发）

### 3.2 建树规则（用户验收规格）

- 遍历 `ToolStack.getUpgrades()`（额外 modifier，与材料自带分开存）
- 每个 modifier 在 `TINKER_STATION` 配方里找 `IDisplayModifierRecipe`（按 `ModifierId` 匹配）
- **一个 modifier 配方 = 一组**，组内为一个该配方的「匠魂工作站」配方图块投影
  （`RecipeLayoutProjection(layout, Optional.empty(), Map.of())`）→
  `bookmarkList.addRecipeLayoutProjectionBookmarkGroup(List.of(projection), false)`
- 图块的输入槽 = 原材料：gilded→金黑石、brushing→羽毛+铜锭、farsighted→45 胡萝卜……（同一配方放一起）
- **不链式**加入原材料的工作台合成/砂铸等配方；JEI tag 类别（`tag_recipes/*`）一律忽略
- 分组后 fork 内部会自动 `setCraftingMode(groupId, true)`（树组可直接拉取/自动合成）

### 3.3 配方布局解析

`findModifierRecipeLayout(material, modifierId)`：
- 用 `{INPUT: material}` focus 的类别查找（注意是 INPUT 角色，材料是配方的输入槽，不是输出）
- 首个非 tag 类别里、匹配 `IDisplayModifierRecipe` 且 `ModifierId` 相同的配方 → `createRecipeLayoutDrawable(...)`

---

## 4. ME 无线拉取（V / shift+V）

### 4.1 架构（直接复用 JEI fork 的拉取，不自造轮子）

- **拉取入口**：V/shift+V 完全走 fork 自己的 `BookmarkInputHandler.handleBookmarkPull`（组命中、`BookmarkContainerPullExecutor` 规划、`BookmarkContainerPacketHandler` 发包、声音全在 fork）。本模组不拦截、不自定义规划。
- **快照来源（唯一接入点）**：fork 在拉取前调用 `BookmarkExternalStorageSnapshots.scan(menu, screen, keyFactory)`，按注册顺序取第一个非空快照。
  - ⚠️ ME 网络数据**只存在于服务端**；客户端不开终端时没有快照来源（fork 自己的 AE2 提供器也依赖已打开终端的客户端 repo；客户端侧 `getMenuHost/getConnectedGrid` 拿不到服务端网格）。
  - 因此用 payload **定时同步**：客户端每 32 tick（有屏幕打开时）发 `WirelessSnapshotRequestPayload`；服务端 `EaepCompatBridge.readNetworkEntries`（EAEP 定位 → `getConnectedGrid` → `getAvailableStacks` 枚举）回 `WirelessSnapshotDataPayload`；客户端写入 `WirelessSnapshotCache`。
- **提供器** `WirelessExternalStorageSnapshotProvider`（TenshisJei 构造器客户端侧注册）：
  - 已打开 ME 终端菜单 → 返回空（fork 自己的 AE2 提供器用菜单客户端 repo，最直接）；
  - 否则**纯读缓存**：`WirelessSnapshotCache.getEntries()` → 转 fork 的 `Entry` → `BookmarkExternalStorageSnapshots.createSnapshot(entries, keyFactory)`。
- **服务端抽取**：fork 收到 `PacketPullBookmarkItems` 后走 `ServerBookmarkPullTransfers` 处理器链；本模组注册 `WirelessBookmarkPullTransferHandler`（fork 的 AE2 handler 之后）：EAEP-first（`WirelessTerminalLocator.find → getConnectedGrid → StorageHelper.poweredExtraction`，成功时 `useTerminalPower + located.commit()`），EAEP 缺省时自研 AE2 直连（背包/Curios 定位 + `getMenuHost` + `getLinkStatus`）；失败返回空交给 fork 回退。

#### 踩坑记录（重要）

1. **客户端快照门**：fork 规划要求快照里**有**网络物品；不开终端 → 外部快照为空 → 计划量为 0 → 不发包。必须先让快照里有网络物品。
2. **自定义合成存储（自造轮子）教训**：曾尝试在客户端合成"无限外部存储"（全部输入 / 排除产物 / 只放叶子三种方案），最终都会在某些链形下出错：
   - 全放 → 链内中间物被"外部存货"提前满足，**基础原材料需求短路**（多配方链拉不到原材料）；
   - 排除产物（RESULT）→ 单配方正常，但多配方链仍有中间物短路问题；
   - 只放链叶子 → 依赖手工判定"链内可自产"，语义与 fork 链数学重复。
   fork 的 `BookmarkPullPlanner.plan` 已正确处理全部情形，**喂真实快照即可**，不要在客户端重算。
3. **客户端无网格**：客户端侧 `WirelessTerminalLocator.getConnectedGrid` 在不开终端时拿不到服务端网格（网络数据不向客户端同步），所以快照必须靠**服务端枚举 + payload 同步**。
4. **多配方链语义**：基础原材料充足时，fork 链数学把需求传播到最深层（链内无配方可产出的输入），正常拉原材料；链内有中间物且网络里也有时 pull 的是链上可满足的需求点，这是 fork 的既定语义，不干预。
5. **蓝色框架（组面板槽）V 拉取**：fork 的 `getPullGroupIdUnderMouse` 本就覆盖组面板槽，无需额外处理。
6. **freeSlots 门**：fork 规划在 `freeSlots <= 0` 时直接给空计划（背包满时"无物可拉"是预期行为，不是 bug）。

### 4.2 服务端日志（调试用，`[Tenshi's JEI Addon/]` 前缀）

- `pull-srv: handler entered, targets=..., menu=..., containerId=...` — 处理器被调用
- `pull-srv: EAEP find -> ... / getConnectedGrid -> ... / extract ... -> extracted N / moved = N` — EAEP 路径每步

### 4.3 数据流

```
V/shift+V (fork handleBookmarkPull)
  -> BookmarkExternalStorageSnapshots.scan(menu, screen, keyFactory)
     -> 本模组 Provider：读 WirelessSnapshotCache -> createSnapshot
        （缓存由 payload 定时同步：客户端请求 -> 服务端 EAEP 枚举 -> 回传）
  -> BookmarkContainerPullExecutor.pull(Request, PacketHandler)（fork 规划+发包）
  -> PacketPullBookmarkItems(containerId, targets) -> 服务端 ServerBookmarkPullTransfers
     -> fork AE2 handler（仅 ME 终端菜单） / WirelessBookmarkPullTransferHandler（EAEP-first）
     -> ServerBookmarkExternalStoragePull.pull -> poweredExtraction 落物品进背包
```

---

## 5. 构建与运行

- 工具链：JDK 21；Gradle 8.10.2（`D:/gradle-8.10.2/bin/gradle.bat --no-daemon`）；
  `GRADLE_USER_HOME=E:/java mod lib/dsh_workspace/.gradle-home3`
- 依赖（build.gradle `compileOnly files(...)`，全部放 `run/mods`）：
  JEIunofficial fork jar、ExtendedTerminal、appliedenergistics2、TinkersConstruct 3.12.x、Mantle、
  curios、extendedae_plus + ae2wtlib + ExtendedAE + architectury + cloth-config + Glodium
- 构建：`gradle build`；开发运行：`gradle runClient`（约 2 分钟到主菜单）；
  日志：`run/logs/latest.log`（调试行 `[ET-jei] ...`）
- 发布 jar：`build/libs/tenshis_jei_addon-1.21.1-1.0.0.jar`（compileOnly，不带依赖）

---

## 6. 已知边界 / 待办

- 树组的「已收藏配方才链式展开」尚未实现（当前规格=只放原材料本身；若需要可加 favorites 门控）
- 快照同步是定时（约 1.6s 一次）而非事件驱动：刚打开界面立刻按 V 可能用到上一轮数据（物品数量最多差 1.6s）；网络物品变化后最长 1.6s 生效
- 客户端 freeSlots 判定与 fork 一致（空槽或未满堆算空）；背包满时 planner 直接给空计划
- `ae2wtlib` 的 fork mixin ClassNotFound 警告无害（未装时 fork 自动跳过）
- Curios tag 缺引用警告（数据包引用缺失物品）无害
- 既有: ET 终端提供器修复、批处理 mixin、链数学日志 mixin（未在本文展开）

---

## 9. 拉取不足自动合成（V/shift+V，v1.0.2）
- 客户端 mixin `BookmarkPullPlannerAutoCraftMixin` 注入 `BookmarkPullPlanner.plan` RETURN：
  用 fork 公开的 `RecipeChainMath.refresh` 取完整需求，缺口 = required - available（`matchesCraftingAvailable` 宽松匹配快照），
  经 C2S `CraftRequestPayload` 发服务端。
- 服务端 `EaepCompatImpl.autoCraft` -> EAEP 定位 + `grid.getCraftingService()`，逐个 `beginCraftingCalculation` + `submitJob`。
- 关键坑：simRequester 须返回 `grid.getPivot()`（否则拿不到库存、plan.simulation()==true）；
  勿在主线程 `job.get()` 阻塞（后台线程等结果 + 主线程 submitJob）；无样板物品先 `isCraftable` 跳过。
- 1.21.1 用 `ItemStack.parseOptional(registryAccess, tag)` 重建代表栈（无 `ItemStack.of`）。
