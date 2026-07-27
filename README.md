# I18nAutoUpdateMod

[![Modrinth](https://img.shields.io/modrinth/v/PWERr14M?logo=modrinth&label=Modrinth)](https://modrinth.com/mod/PWERr14M)
[![License](https://img.shields.io/badge/license-AGPL--3.0--only-blue)](LICENSE)

一个更好的自动下载、更新、合并并应用「[简体中文资源包（Minecraft Mod Language Package）](https://github.com/CFPAOrg/Minecraft-Mod-Language-Package)」的客户端 Mod。

## 功能

- 更新任务完全在后台线程执行，不阻塞游戏启动。
- 直接从资源包发布源下载，不依赖可能延迟更新的 Index 文件。
- 根据网络位置与实际响应速度选择下载源；超时、404 或 MD5 校验失败时自动切换。
- 根据 Minecraft 版本及 Loader 自动选择、合并资源包，并转换对应的 `pack.mcmeta`。
- 使用临时文件和原子替换更新资源包，下载失败时继续保留已有缓存。
- 自动将语言包加入资源包列表底部，避免覆盖其他自定义资源包。
- 同一个 JAR 支持 MinecraftForge、NeoForge、Fabric 和 Quilt。

## 下载

本项目通过 [Modrinth](https://modrinth.com/mod/PWERr14M) 和 [GitHub Releases](https://github.com/ChouChiu/I18nAutoUpdateMod/releases) 发布。

## 安装

1. 从 Modrinth 或 GitHub Releases 下载适用于当前版本的 JAR。
2. 将 JAR 放入 Minecraft 实例的 `mods` 文件夹。
3. 启动游戏，Mod 会在后台检查并更新语言包。

已有缓存会在本次启动继续使用。首次下载或后台生成的新资源包通常从下次启动开始加载，不需要手动将其加入资源包列表。

## 支持范围

- Minecraft：声明支持 1.6.1 至 26.2，具体版本见 [`gradle.properties`](gradle.properties)
- Mod Loader：MinecraftForge、NeoForge、Fabric、Quilt
- Java：遵循对应 Minecraft 与 Loader 的要求；Mod 字节码目标为 Java 8

Quilt 使用 Fabric 资源；NeoForge 使用 Forge 资源。缺少专用变体时会稳定回退到 Forge 资源。

## 资源包来源

资源包从 CFPA 的固定 `autobuild` Release、CFPA 下载服务及可用镜像直接获取。海外网络优先使用 GitHub；中国大陆网络会并发探测国内源，同时保留其他来源作为回退。

当前使用的官方资源版本包括 1.10.2、1.12.2、1.16、1.18、1.19、1.20、1.21 和 26.1。

缓存保存在系统数据目录下的 `.i18nautoupdatemod` 目录。旧版 `.i18nupdatemod` 数据会自动迁移，已有的新缓存不会被旧文件覆盖。

> [!NOTE]
> 网易版 Minecraft 不会执行在线资源包下载。

## 开发

构建环境使用 JDK 25 和 Gradle Wrapper 9.6.1：

```shell
./gradlew clean test shadowJar
```

构建产物位于 `build/libs/I18nAutoUpdateMod-1.0.0-all.jar`。

## 许可证

项目采用 [AGPL-3.0-only](LICENSE) 许可证。
