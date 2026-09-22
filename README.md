# I18nAutoUpdateMod

[![Modrinth](https://img.shields.io/modrinth/v/mEn7eS3l?logo=modrinth&label=Modrinth)](https://modrinth.com/mod/mEn7eS3l)
[![License](https://img.shields.io/badge/license-AGPL--3.0--or--later-blue)](https://github.com/ChouChiu/I18nAutoUpdateMod/blob/dev/LICENSE)

为 Minecraft 自动下载、更新并应用 CFPA [简体中文模组汉化包](https://github.com/CFPAOrg/Minecraft-Mod-Language-Package) 的客户端模组。

本项目基于 [CFPAOrg/I18nUpdateMod3](https://github.com/CFPAOrg/I18nUpdateMod3) 重构，移除了启动阻塞逻辑，优化了镜像源竞速与容灾机制，并提供单 JAR 全 Loader 跨版本兼容。

## 特性

- 更新检查与下载均在独立守护线程完成，绝不拖慢游戏启动速度。
- 单一 JAR 文件同时兼容 MinecraftForge、NeoForge、Fabric 与 Quilt，无需按加载器分别下载。
- 直接对接官方发布源，不依赖更新易滞后的索引文件。
- 国内网络并发探测可用镜像源，海外直连 GitHub，遇到超时、404 或校验失败自动无感回退。
- 下载完成并校验 MD5 后再原子替换现有文件，网络中断或下载失败绝不损坏已有缓存。
- 根据当前游戏版本与 Loader 自动合并汉化资源，并生成适配当前版本的 `pack.mcmeta`（支持 1.6.1 到 26.3）。
- 自动将汉化包注册在资源包列表最底层，不与玩家自定义的材质包抢优先级。

## 安装与使用

1. 前往 [Modrinth](https://modrinth.com/mod/mEn7eS3l) 或 [GitHub Releases](https://github.com/ChouChiu/I18nAutoUpdateMod/releases) 下载最新 JAR。
2. 将 JAR 放入客户端的 `mods` 文件夹，启动游戏即可。

本模组仅需在客户端安装，服务端无需安装。首次启动若下载了新汉化包，通常在下次进入游戏时加载生效；已有缓存会自动继续使用，无需手动在资源包界面调整。

## 兼容性

- 游戏版本：Minecraft 1.6.1 至 26.3（具体版本见 [gradle.properties](https://github.com/ChouChiu/I18nAutoUpdateMod/blob/dev/gradle.properties)）
- 模组加载器：MinecraftForge、NeoForge、Fabric、Quilt
- Java 环境：模组字节码目标为 Java 8，运行时遵循对应 Minecraft 与 Loader 的 Java 要求。

## 补充说明

- 缓存路径：汉化包缓存存放于用户数据目录下的 `.i18nautoupdatemod` 中。若先前使用过原版模组，旧版 `.i18nupdatemod` 缓存会自动平滑迁移。
- 网易版环境：检测到网易版 Minecraft 环境时将自动跳过在线下载，以遵守其开发者规范。

## 构建

项目使用 JDK 25 和 Gradle Wrapper：

```shell
./gradlew clean test shadowJar
```

构建产物位于 `build/libs/I18nAutoUpdateMod-1.0.0-all.jar`。

## 开源协议

本项目采用 [AGPL-3.0-or-later](https://github.com/ChouChiu/I18nAutoUpdateMod/blob/dev/LICENSE) 协议开源。

---

## English

A Minecraft client-side mod that automatically downloads, updates, and applies the CFPA [Minecraft Mod Language Package](https://github.com/CFPAOrg/Minecraft-Mod-Language-Package) for Simplified Chinese translations.

Refactored from [CFPAOrg/I18nUpdateMod3](https://github.com/CFPAOrg/I18nUpdateMod3) to eliminate game startup delays, improve mirror reliability and failover, and provide universal multi-loader compatibility within a single JAR.

### Features

- Updates are checked and downloaded on a background daemon thread, never slowing down game launch.
- A single JAR supports MinecraftForge, NeoForge, Fabric, and Quilt out of the box.
- Downloads directly from release sources instead of delayed index files.
- Concurrently probes mainland China mirrors while preferring GitHub abroad, with automatic fallback on timeout, 404, or checksum mismatch.
- Downloads to temporary files and verifies MD5 checksums before replacing, ensuring existing cache is never corrupted.
- Dynamically merges language assets and generates the correct `pack.mcmeta` format for your Minecraft version (1.6.1 through 26.3).
- Automatically placed at the bottom of the resource pack list, preserving custom texture pack priority.

### Installation

1. Download the latest JAR from [Modrinth](https://modrinth.com/mod/mEn7eS3l) or [GitHub Releases](https://github.com/ChouChiu/I18nAutoUpdateMod/releases).
2. Place the JAR into your `.minecraft/mods` directory.

Client-side only; do not install on dedicated servers. Newly downloaded packs take effect on the next game launch; existing cached packs are used immediately without manual intervention.

### Compatibility

- Minecraft: 1.6.1 to 26.3 (see [gradle.properties](https://github.com/ChouChiu/I18nAutoUpdateMod/blob/dev/gradle.properties))
- Loaders: MinecraftForge, NeoForge, Fabric, and Quilt
- Java: Targets Java 8 bytecode; runtime requirements depend on the selected Minecraft version and loader.

### Notes

- Cache location: Cached files are stored in `.i18nautoupdatemod` under the platform user data directory. Existing cache from the legacy `.i18nupdatemod` folder is migrated automatically.
- NetEase client: Online downloads are automatically disabled when the NetEase Minecraft environment is detected.

### Building

Requires JDK 25 and Gradle Wrapper:

```shell
./gradlew clean test shadowJar
```

Artifacts are output to `build/libs/I18nAutoUpdateMod-1.0.0-all.jar`.

### License

Licensed under [AGPL-3.0-or-later](https://github.com/ChouChiu/I18nAutoUpdateMod/blob/dev/LICENSE).
