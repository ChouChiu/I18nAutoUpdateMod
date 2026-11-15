# I18nAutoUpdateMod

[![Modrinth](https://img.shields.io/modrinth/v/mEn7eS3l?logo=modrinth&label=Modrinth)](https://modrinth.com/mod/mEn7eS3l)
[![License](https://img.shields.io/badge/license-AGPL--3.0--or--later-blue)](https://github.com/ChouChiu/I18nAutoUpdateMod/blob/dev/LICENSE)

为 Minecraft 自动下载、更新并应用 CFPA [简体中文模组汉化包](https://github.com/CFPAOrg/Minecraft-Mod-Language-Package) 的客户端模组。

本项目基于 [CFPAOrg/I18nUpdateMod3](https://github.com/CFPAOrg/I18nUpdateMod3) 重构，移除了启动阻塞逻辑，优化了镜像源竞速与容灾机制，并提供单 JAR 全 Loader 跨版本兼容。

## 特性

- 模组资源扫描、更新检查和下载均在后台执行，已有资源包时无需等待，原始包缓存有效时不访问下载源
- 单一 JAR 文件同时兼容 MinecraftForge、NeoForge、Fabric 与 Quilt，无需按加载器分别下载。
- 直接对接官方发布源，不依赖更新易滞后的索引文件。
- 国内网络并发探测可用镜像源，海外直连 GitHub，遇到超时、404 或校验失败自动无感回退。
- 下载后检查 MD5 和 ZIP 结构，转换时限制展开大小并校验条目完整性，失败时保留已有可用文件
- 根据当前游戏版本与 Loader 自动合并汉化资源，并生成适配当前版本的 `pack.mcmeta`（支持 1.6.1 到 26.3）。
- 默认自动将汉化包注册在资源包列表最底层，不与玩家自定义的材质包抢优先级。

## 安装与使用

1. 前往 [Modrinth](https://modrinth.com/mod/mEn7eS3l) 或 [GitHub Releases](https://github.com/ChouChiu/I18nAutoUpdateMod/releases) 下载最新 JAR。
2. 将 JAR 放入客户端的 `mods` 文件夹，启动游戏即可。

本模组仅需在客户端安装，服务端无需安装。首次启动且游戏内没有生成好的资源包时，最多等待配置指定的秒数，若超时则继续在后台下载，Minecraft 26.1 及以上会在生成后尝试自动启用，旧版在下次启动时启用；已有资源包时直接使用

## 兼容性

- 游戏版本：Minecraft 1.6.1 至 26.3（具体版本见 [gradle.properties](https://github.com/ChouChiu/I18nAutoUpdateMod/blob/dev/gradle.properties)）
- 模组加载器：MinecraftForge、NeoForge、Fabric、Quilt
- Java 环境：模组字节码目标为 Java 8，运行时遵循对应 Minecraft 与 Loader 的 Java 要求。

## 设置

首次启动后会在游戏目录生成 `config/i18nautoupdatemod.json`，修改后重启游戏生效

```json
{
  "forceBottom": true,
  "defaultSource": "auto",
  "mirrorPriority": ["CFPA", "Community", "GitHub"],
  "betaPack": false,
  "mergeLoaders": false,
  "initialTimeout": 10,
  "cacheTtlHours": 24,
  "excludeMods": [],
  "onlyWhenChineseSystem": false
}
```

- `forceBottom`：是否每次启动都把汉化包强制移到资源包列表最底层，关闭后保留玩家手动调整的位置，仅首次添加时置底
- `defaultSource`：默认下载源，可选 `auto` / `GitHub` / `CFPA` / `Community`，`auto` 为按地区自动测速选择
- `mirrorPriority`：默认源之外的回退顺序，未列出的源自动追加到末尾
- `betaPack`：是否使用 Beta 资源包，优先下载 [CFPA-AutoMerge](https://github.com/ChouChiu/CFPA-AutoMerge) 每 6 小时合并全部未合并 PR 后打包的版本，下载失败时回退正式包
- `mergeLoaders`：不区分加载器，同时下载并合并 Forge 与 Fabric 汉化包，翻译冲突时以当前加载器的版本为准
- `initialTimeout`：首次启动无本地资源包时的最大等待秒数，设为 `0` 即完全不等待
- `cacheTtlHours`：更新检查间隔（小时），设为 `0` 即每次启动都检查，Beta 资源包最长 6 小时检查一次
- `excludeMods`：不参与汉化的模组命名空间，如 `["create"]`
- `onlyWhenChineseSystem`：仅在操作系统区域设置为中文时运行，非中文系统下不联网、不下载、不改动 `options.txt`，判定依据是系统区域设置而非游戏内语言，适合整合包作者随包分发

## 补充说明

- 启动等待配置：JVM 参数 `-Di18nautoupdatemod.initialTimeout=<秒数>` 优先于配置文件中的 `initialTimeout`
- 缓存路径：汉化包缓存存放于用户数据目录下的 `.i18nautoupdatemod` 中。若先前使用过原版模组，旧版 `.i18nupdatemod` 缓存会自动平滑迁移。
- 网易版环境：检测到网易版 Minecraft 环境时将自动跳过在线下载，以遵守其开发者规范。

## 构建

项目使用 JDK 25 和 Gradle Wrapper：

```shell
./gradlew clean test shadowJar
```

构建产物位于 `build/libs/I18nAutoUpdateMod-<version>-all.jar`。

## 开源协议

本项目采用 [AGPL-3.0-or-later](https://github.com/ChouChiu/I18nAutoUpdateMod/blob/dev/LICENSE) 协议开源。

---

## English

A Minecraft client-side mod that automatically downloads, updates, and applies the CFPA [Minecraft Mod Language Package](https://github.com/CFPAOrg/Minecraft-Mod-Language-Package) for Simplified Chinese translations.

Refactored from [CFPAOrg/I18nUpdateMod3](https://github.com/CFPAOrg/I18nUpdateMod3) to eliminate game startup delays, improve mirror reliability and failover, and provide universal multi-loader compatibility within a single JAR.

### Features

- Mod scanning, update checks, and downloads run in the background; an installed pack is used immediately, and fresh source caches skip network checks
- A single JAR supports MinecraftForge, NeoForge, Fabric, and Quilt out of the box.
- Downloads directly from release sources instead of delayed index files.
- Concurrently probes mainland China mirrors while preferring GitHub abroad, with automatic fallback on timeout, 404, or checksum mismatch.
- Checks the MD5 and ZIP structure before replacing cached downloads, and checks entry integrity and expanded size during conversion
- Dynamically merges language assets and generates the correct `pack.mcmeta` format for your Minecraft version (1.6.1 through 26.3).
- Placed at the bottom of the resource pack list by default, preserving custom texture pack priority.

### Installation

1. Download the latest JAR from [Modrinth](https://modrinth.com/mod/mEn7eS3l) or [GitHub Releases](https://github.com/ChouChiu/I18nAutoUpdateMod/releases).
2. Place the JAR into your `.minecraft/mods` directory.

Client-side only; do not install on dedicated servers. On first launch without a generated pack, the game waits up to the configured timeout; if the wait times out, Minecraft 26.1 and newer try to enable the pack once it is ready, while older versions enable it on the next launch

### Compatibility

- Minecraft: 1.6.1 to 26.3 (see [gradle.properties](https://github.com/ChouChiu/I18nAutoUpdateMod/blob/dev/gradle.properties))
- Loaders: MinecraftForge, NeoForge, Fabric, and Quilt
- Java: Targets Java 8 bytecode; runtime requirements depend on the selected Minecraft version and loader.

### Settings

A config file is generated at `config/i18nautoupdatemod.json` in the game directory on first launch, and changes take effect after restarting the game

- `forceBottom`: Move the language pack to the bottom of the resource pack list on every launch, when disabled the position you set manually is kept and the pack is only placed at the bottom when first added
- `defaultSource`: Preferred download source, one of `auto` / `GitHub` / `CFPA` / `Community`, where `auto` picks a source by location and probing
- `mirrorPriority`: Fallback order of the other sources, unlisted sources are appended automatically
- `betaPack`: Prefer the beta packs from [CFPA-AutoMerge](https://github.com/ChouChiu/CFPA-AutoMerge), rebuilt every 6 hours with all open pull requests merged, falling back to the stable packs when the download fails
- `mergeLoaders`: Ignore the loader and merge both the Forge and Fabric packs, with the current loader's translations winning on conflicts
- `initialTimeout`: Maximum seconds to wait on first launch without local cache, set to `0` to disable waiting
- `cacheTtlHours`: Update check interval in hours, set to `0` to check on every launch, beta packs are checked at least every 6 hours
- `excludeMods`: Mod namespaces to leave untranslated, such as `["create"]`
- `onlyWhenChineseSystem`: Only run when the operating system locale is Chinese, otherwise nothing is downloaded and `options.txt` is left untouched, the check uses the system locale rather than the in-game language

### Notes

- Startup timeout configuration: The JVM property `-Di18nautoupdatemod.initialTimeout=<seconds>` takes precedence over `initialTimeout` in the config file
- Cache location: Cached files are stored in `.i18nautoupdatemod` under the platform user data directory. Existing cache from the legacy `.i18nupdatemod` folder is migrated automatically.
- NetEase client: Online downloads are automatically disabled when the NetEase Minecraft environment is detected.

### Building

Requires JDK 25 and Gradle Wrapper:

```shell
./gradlew clean test shadowJar
```

Artifacts are output to `build/libs/I18nAutoUpdateMod-<version>-all.jar`.

### License

Licensed under [AGPL-3.0-or-later](https://github.com/ChouChiu/I18nAutoUpdateMod/blob/dev/LICENSE).
