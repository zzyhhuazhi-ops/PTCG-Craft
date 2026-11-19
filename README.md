<div align="center">

# PTCG Craft

本人是 PTCG Craft 的开发者，现使用 MC 百科账号「zz幽魂」（UID：1558481）申请成为开发者

**在 Minecraft 中收集、展示与对战宝可梦卡牌。**

[![Release](https://img.shields.io/github/v/release/zzyhhuazhi-ops/PTCG-Craft?style=flat-square&color=5865F2)](https://github.com/zzyhhuazhi-ops/PTCG-Craft/releases/latest)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62B47A?style=flat-square)
![Loader](https://img.shields.io/badge/Loader-Fabric-DBB98D?style=flat-square)
![Java](https://img.shields.io/badge/Java-21-E89545?style=flat-square)

[**下载 Mod**](https://github.com/zzyhhuazhi-ops/PTCG-Craft/releases/latest) · [安装指南](#安装) · [功能一览](#功能) · [反馈问题](https://github.com/zzyhhuazhi-ops/PTCG-Craft/issues)

</div>

## 0.8.6 · 德尔塔统御

新增 **DLR／德尔塔统御**卡包：**141 张卡牌、4 款封面**，每包 5 张、20 宝可币。包含对应战斗规则及双张竞技场支持；中文卡文为非官方翻译。

## 安装

| 环境 | 要求 |
| :--- | :--- |
| Minecraft Java | 1.21.1 |
| 模组加载器 | Fabric Loader 0.16.10 或更新版本 |
| 依赖 | Fabric API（1.21.1 对应版本）、Java 21 |
| 多人游戏 | 客户端与服务器安装同一版本 |

1. 安装 Fabric 与对应版本的 Fabric API。
2. 从 [Releases](https://github.com/zzyhhuazhi-ops/PTCG-Craft/releases/latest) 下载 `ptcg-craft-0.8.6.jar`，放入游戏的 `mods` 文件夹。
3. 启动游戏，通过卡盒、手机和卡桌开始收藏与对战。

内置缩略卡图即可游玩；高清原画需另装资源包。GitHub 自动生成的 Source code 压缩包用于开发，不是安装包。

## 功能

| 玩法 | 内容 |
| :--- | :--- |
| 对战 | 双人对战、四档 AI、战书邀战、卡表导入、新手随机卡组 |
| 操作 | 拖放出牌、招式与特性面板、撤退选能量、动画与音效 |
| 收藏 | 图鉴、卡盒、900 格随身卡册、个人履历与胜率 |
| 开包 | 世代商城、收藏卡包、罕度筛选、获取查询、最多 64 包连开 |
| 经济 | 卡牌回收、品质鉴定、交易所、三级租赁机与燃料加速 |
| 展示 | 上锁展示柜、多规格壁挂框、全息展示、拼接观战屏与弹幕 |

部分旧世代宝可梦规则仍未支持；可用性以游戏内规则检查为准。

## 源码构建

使用 **JDK 21**。项目自带 Gradle Wrapper，无需另装 Gradle。

```powershell
# Windows PowerShell，在仓库目录运行
$env:GRADLE_USER_HOME = "$PWD/.gradle-cache"
./gradlew.bat --no-daemon assemble
```

Linux / macOS 使用 `GRADLE_USER_HOME="$PWD/.gradle-cache" ./gradlew --no-daemon assemble`。
成品输出至 `build/libs/`；源码包含构建所需卡牌数据、模型和缩略图。

## 反馈与致谢

请在 [Issues](https://github.com/zzyhhuazhi-ops/PTCG-Craft/issues) 附上模组版本、复现步骤和相关日志；卡牌问题请注明系列及编号。

基于 Fabric 开发，界面字体使用 Noto Sans SC，字体许可随资源保留。独立同人项目，与 Pokémon 官方无关联；卡牌图像等素材的权利归原权利人所有。
