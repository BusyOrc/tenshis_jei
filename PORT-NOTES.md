# Tenshi's JEI Addon - Forge 1.20.1 移植笔记

来源：tenshis_jei（NeoForge 1.21.1）忠实移植；忽略 ExtendedTerminal/compat-et 部分。
1.21.1 原仓库保持只读原样，本目录为独立 1.20.1 工程。

## 环境
- Forge 1.20.1 (47.3.1), ModDevGradle legacyforge 2.0.86, Java 17 工具链 (gradle 跑在 21)
- 依赖 jars 由用户放本目录根 (JEIunofficial-1.20.1-forge-15.48.0.183.jar 等)
- 构建: D:/gradle-8.10.2/bin/gradle.bat --no-daemon compileJava/build

## 移植状态
- [x] 建 1.20.1 工程 (settings/gradle.properties/build.gradle, mods.toml, mixins.json 精简, 删 ET)
- [x] config -> ForgeConfigSpec (仅 DEBUG), 删 RecipeTreeCraftingMode/TenshisJeiCraftingModes
- [x] 首次编译跑通 -> 枚举 93 个 API 差异错误
- [x] 主类 loader 层 (Forge @Mod, FMLJavaModLoadingContext, DistExecutor) + 删 ET 注册
- [x] compat bridges ModList import
- [x] network -> Forge SimpleChannel (请求/数据两包 + 客户端 tick)
- [x] ae2 handler 简化为 EAEP-only（删自建 AE2 直连回退与 curios）；Provider 读缓存对齐 mezz-1.20.1
- [x] eaep 对齐 EAEP-1.20.1 WirelessTerminalLocator（同 API）；删 curios；tinkers 适配 MC1.20.1 无 RecipeHolder
- [x] mixins 目标核对 fork-1.20.1 (handleUserInput/calculateScrollStepArea 均存在)
- [x] 编译干净 (compileJava 0 errors)
- [ ] runClient 启动验证 (后台运行中 pwsh-7)
- [ ] (用户) 游戏内验证
- [ ] 上传 GitHub 分支 forge 1.20.1

## 关键移植修正（runClient 排查中修复）
- mods.toml 依赖格式：Forge 1.20.1 用 `mandatory = true/false`（非 NeoForge 的 `type`）。
- mixins.json compatibilityLevel: JAVA_21 -> JAVA_17（Mixin 0.8.5 / Java 17）。
- 两个 mixin 的 @Mixin/@Inject/@Shadow 加 `remap = false`（目标是 mezz 类、无混淆映射）。

## dev 客户端（runClient）结论
- 代码编译/构建均通过（ModDevGradle 与 ForgeGradle 6 两条链）。
- dev 运行被第三方 mod 的 SRG 命名 mixin 阻塞（AE2 PickColorMixin f_91074_、Curios MixinInventory f_35978_）
  在 dev 命名映射下无法应用；跨 ModDevGradle/ForgeGradle、official/parchment 都复现。
  生产 SRG 运行时这些 mod 均正常，与 tenshis_jei_addon 代码无关。
- 最终交付：build/libs/tenshis_jei_addon-1.0.1.jar（Forge 47.4.20，含全部修正），
  建议放入真实 Forge 1.20.1 实例 mods 目录实测。

## 自动合成触发（V/shift+V 拉取不足时，v1.0.2）
- 客户端 mixin `BookmarkPullPlannerAutoCraftMixin` 注入 `BookmarkPullPlanner.plan` RETURN：
  用 fork 公开的 `RecipeChainMath.refresh` 取完整需求，缺口 = required - available，
  经 `CraftRequestPacket`(C2S) 发服务端。
- 服务端 `EaepCompatImpl.autoCraft` -> EAEP 定位 + `grid.getCraftingService()`，
  逐个 `beginCraftingCalculation` + `submitJob`。
- 关键坑：
  1) `simRequester` 必须返回 `grid.getPivot()`（网格节点），否则合成计算拿不到库存、
     plan.simulation()==true 表现为"不可合成"；
  2) 不能在主线程 job.get() 阻塞——用后台线程等结果 + server.execute(submitJob)；
  3) 无样板物品先 `isCraftable` 跳过。
- 日志：全部走 `TenshisJeiLog`，仅配置里 debug=true 才输出。

## 工匠砧自动强化 —— 调研与设计（进行中）

### EtST-Lib 工具 UUID（已确认，只读）
- UUID 存在工具 ItemStack NBT 键 `"etstlib_tool_uuid"`（`CommonConstants.KEY_TOOL_UUID`）。
- 读取：`com.c2h6s.etstlib.util.IToolUuidGetter.getUuidForItem(ItemStack)` -> Optional<UUID>（走 TOOL_UUID capability）；
  或直接 `nbt.getString("etstlib_tool_uuid")`（无 capability 依赖的回退）。
- 计划：加 `compat/etstlib/EtstLibCompatBridge`（ModList 判定 `etstlib`）+ `EtstLibCompatImpl`，只读 UUID。

### fork 自动合成机制（已确认）
- fork 入口：`BookmarkInputHandler.handleBookmarkAutoCrafting`（`BookmarkAutoCraftingActivator.isAutoCraftingInput` = shift + craftKey）。
- 触发前提：鼠标悬停一个**处于合成模式的收藏组** + 当前容器 Screen。
- 取该组 `bookmarkList.getRecipeChainInputs(groupId)` + `getCollapsedRecipeIds` -> `BookmarkAutoCraftingBridge.activate(...)`
  -> `PacketCraftingGridCraft(containerId, multiplier, targetStacks)` -> 服务端 craft executor。
- 服务端：`CraftingGridCraftExecutors` 里 fork 已注册 `TinkerCraftingGridCraftExecutor`（处理 `CraftingStationContainerMenu`/`TinkerStationContainerMenu`，
  填 `craftingSlots()`=材料输入槽 + shift 点结果槽，站自身 quickMoveStack 完成合成取出强化工具）。
- 砧 = `TinkersAnvilBlock extends TinkerStationBlock` -> `TinkerStationBlockEntity` -> `TinkerStationContainerMenu`（slot0=工具槽）。

### 待确认的分歧（与用户对 UX 的表述）
- fork 的自动合成触发是"鼠标悬停收藏组 + 合成键"，并非"砧 GUI 打开就按 shift+F"。
- 需要确认：砧自动强化到底复用什么触发路径（a: fork 的收藏组悬停+合成键；b: 需要自定义在砧 Screen 上拦截 shift+F，

## 工匠砧自动强化（shift+F）— 已实现（ANVIL 测试版）
- 触发：打开砧 GUI（TinkerStation/CraftingStation menu）且 slot0 放带 EtST-Lib UUID 的工具时按 shift+F，
  鼠标需悬停一个处于合成模式的收藏组（用户把要加的多个 modifier 配方**手动合并进同一个组**）。
- 设计关键：fork 的 `TinkerCraftingGridAccess.find(TinkerStationContainerMenu)` 把 craftingSlots 取为
  `getInputSlots()`（材料/槽位槽），**slot0 工具槽不属于 craftingSlots**；executor 只往材料槽填料再 shift 点结果槽。
  因此链式多次 modifier 会**顺序施加到 slot0 同一把工具上**（无需每步 UUID 回槽；工具始终留在 slot0）。
- 实现：`BookmarkInputHandlerTinkersShiftFMixin.handleAnvilShiftF` 真实分支在（uuid 非空 && 悬停合成组）时调用
  `runAnvilGroup`，完整复刻 fork `handleBookmarkAutoCrafting` 的 `BookmarkAutoCraftingBridge.createTask(...)`
  +`autoCraftingRunner.start(...)`（服务端路径；若 JEI 不在服务端走 createClientFallbackTask 兜底），整链交给 fork 的
  auto-crafting runner 顺序发包执行。新 @Shadow 字段：`serverConnection` / `autoCraftingRunner` / `clientCraftingGridClickRunner`。
- 产物：`tenshis_jei_addon-1.0.2-ANVIL.jar`。
- 待实测确认：多 modifier 合并组在真实砧上是否逐条成功、工具是否确实留在 slot0（若离开 slot0 需再加每步 UUID 回槽 hook）。

## 工匠砧自动强化 R2/R3（ANVIL3）
- 实测发现：fork Task 每次把强化后的工具 shift 出砧结果槽进物品栏、slot0 清空；回槽点击用
  `AbstractContainerMenu.clicked` 只改客户端、**不给服务端发包** → 服务端不同步，第二次只有材料
  上砧、工具没回去。修复：`AnvilAutoReSlot` 改用 `MultiPlayerGameMode.handleInventoryMouseClick`
  （真实发包）回槽。
- 新增 `BookmarkAutoCraftingBridgeTaskConfirmGateMixin`：fork `Task.tick` 里"库存确认"
  （`inventoryHasExpectedResultIncreaseSinceDispatch`）在砧会话激活期间改为"等工具回到 slot0"，
  否则 fork 每步 3s 超时、且在工具回槽前 dispatch 下一个导致竞态。
- 触发键：砧自动强化由 shift+F 改为 **shift+C**（对齐 JEI 默认 craft 键 `keyBindings.getCraftItems()`）。
