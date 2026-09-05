# Tenshi's JEI Addon — Forge 1.20.1

Tenshi's JEI Addon 的 Forge 1.20.1 移植（基于 NeoForge 1.21.1 版，忽略 ExtendedTerminal 部分）。

功能：
- 收藏栏 shift+F：为匠魂工具每个额外 modifier 的原材料建配方树组
- V / shift+V：不开 ME 终端，用 EAEP 的 WirelessTerminalLocator 从 AE2 网络无线拉取配方树物品
- 步数输入栏位右移 20px

依赖（放入 `run/mods/`，或实际实例的 `mods/`）：
JEIunofficial fork 1.20.1-forge、Applied Energistics 2、ExtendedAE_Plus、ExtendedAE、Glodium、
guideme、Tinkers' Construct、Mantle、Curios（版本见 PORT-NOTES.md）。

构建：`gradle build`（Forge 47.4.20）。