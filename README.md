# I18nAutoUpdateMod

[![Modrinth](https://img.shields.io/modrinth/v/mEn7eS3l?logo=modrinth&label=Modrinth)](https://modrinth.com/mod/mEn7eS3l)
[![License](https://img.shields.io/badge/license-AGPL--3.0--or--later-blue)](https://github.com/ChouChiu/I18nAutoUpdateMod/blob/dev/LICENSE)

A client-side mod that automatically downloads, updates, merges, converts, and applies the [Minecraft Mod Language Package](https://github.com/CFPAOrg/Minecraft-Mod-Language-Package), maintained by CFPA, to provide Simplified Chinese translations for Minecraft mods.

This project is a substantially modified fork of [CFPAOrg/I18nUpdateMod3](https://github.com/CFPAOrg/I18nUpdateMod3). It refactors the original project's asynchronous update process, download-source selection, resource-pack metadata, and multi-loader compatibility.

## Features

- Runs the entire update process in a background thread without blocking game startup.
- Downloads resource packs directly from their release sources instead of relying on a potentially delayed index.
- Selects download sources by network location and measured response time, with automatic fallback after a timeout, HTTP 404 response, or MD5 mismatch.
- Selects, merges, and converts resource packs for the detected Minecraft version and mod loader, including the appropriate `pack.mcmeta` format.
- Publishes updates through temporary files and atomic replacement so a failed download cannot damage an existing cached pack.
- Automatically places the language pack at the bottom of the resource-pack list so that custom resource packs can override it.
- Uses one JAR across MinecraftForge, NeoForge, Fabric, and Quilt.

## Download

Releases are available from [Modrinth](https://modrinth.com/mod/mEn7eS3l) and [GitHub Releases](https://github.com/ChouChiu/I18nAutoUpdateMod/releases).

## Installation

1. Download the JAR from Modrinth or GitHub Releases.
2. Place the JAR in the Minecraft instance's `mods` folder.
3. Start the game. The mod will check for and download language-pack updates in the background.

An existing cached pack remains available during the current startup. A pack downloaded or generated in the background will normally be loaded on the next game startup. It does not need to be added to the resource-pack list manually.

## Compatibility

- Minecraft: declared support from 1.6.1 through 26.2; see [`gradle.properties`](https://github.com/ChouChiu/I18nAutoUpdateMod/blob/dev/gradle.properties) for the exact version list
- Mod loaders: MinecraftForge, NeoForge, Fabric, and Quilt
- Environment: client-side only; no server installation is required
- Java: follows the requirements of the selected Minecraft version and loader; the mod targets Java 8 bytecode

Quilt uses the Fabric resource variant. NeoForge uses the Forge variant, with a stable fallback to Forge whenever a dedicated variant is unavailable.

## Resource-Pack Sources

Resource packs are downloaded directly from CFPA's fixed `autobuild` release, CFPA download services, and available mirrors. GitHub is preferred outside mainland China. Within mainland China, domestic sources are probed concurrently while all other sources remain available as fallbacks.

The currently used official resource versions include 1.10.2, 1.12.2, 1.16, 1.18, 1.19, 1.20, 1.21, and 26.1.

Cached data is stored in the `.i18nautoupdatemod` directory under the platform data directory. Data from the former `.i18nupdatemod` directory is migrated automatically without overwriting newer cache files.

Note: the NetEase edition of Minecraft does not perform online resource-pack downloads.

## Development

The build uses JDK 25 and Gradle Wrapper 9.6.1:

```shell
./gradlew clean test shadowJar
```

The bundled artifact is generated at `build/libs/I18nAutoUpdateMod-1.0.0-all.jar`.

## License

This project is licensed under [AGPL-3.0-or-later](https://github.com/ChouChiu/I18nAutoUpdateMod/blob/dev/LICENSE).

## 中文说明

一个自动下载、更新、合并并应用「[简体中文资源包（Minecraft Mod Language Package）](https://github.com/CFPAOrg/Minecraft-Mod-Language-Package)」的客户端 Mod。

本项目是 [CFPAOrg/I18nUpdateMod3](https://github.com/CFPAOrg/I18nUpdateMod3) 的二次开发版本，在原项目基础上重构了异步更新、下载源选择、资源包元数据和多 Loader 兼容逻辑。

### 功能

- 更新任务完全在后台线程执行，不阻塞游戏启动。
- 直接从资源包发布源下载，不依赖可能延迟更新的 Index 文件。
- 根据网络位置与实际响应速度选择下载源；超时、404 或 MD5 校验失败时自动切换。
- 根据 Minecraft 版本及 Loader 自动选择、合并资源包，并转换对应的 `pack.mcmeta`。
- 使用临时文件和原子替换更新资源包，下载失败时继续保留已有缓存。
- 自动将语言包加入资源包列表底部，避免覆盖其他自定义资源包。
- 同一个 JAR 支持 MinecraftForge、NeoForge、Fabric 和 Quilt。

### 安装

1. 从 Modrinth 或 GitHub Releases 下载 JAR。
2. 将 JAR 放入 Minecraft 实例的 `mods` 文件夹。
3. 启动游戏，Mod 会在后台检查并更新语言包。

已有缓存会在本次启动继续使用。首次下载或后台生成的新资源包通常从下次启动开始加载，不需要手动将其加入资源包列表。

### 支持范围

- Minecraft：声明支持 1.6.1 至 26.2
- Mod Loader：MinecraftForge、NeoForge、Fabric、Quilt
- 运行环境：仅客户端，不需要在服务器安装
- Java：遵循对应 Minecraft 与 Loader 的要求；Mod 字节码目标为 Java 8

Quilt 使用 Fabric 资源；NeoForge 使用 Forge 资源。缺少专用变体时会稳定回退到 Forge 资源。

缓存保存在系统数据目录下的 `.i18nautoupdatemod` 目录。旧版 `.i18nupdatemod` 数据会自动迁移，已有的新缓存不会被旧文件覆盖。

注意：网易版 Minecraft 不会执行在线资源包下载。
