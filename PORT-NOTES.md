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
