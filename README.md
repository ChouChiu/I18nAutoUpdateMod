# I18nAutoUpdateMod

[![Version](https://img.shields.io/github/v/release/ChouChiu/I18nAutoUpdateMod?label=&logo=V&labelColor=E1F5FE&color=5D87BF&style=for-the-badge)](https://github.com/ChouChiu/I18nAutoUpdateMod/tags)
[![CurseForge](https://cf.way2muchnoise.eu/short_I18nUpdateMod.svg?badge_style=for_the_badge)](https://www.curseforge.com/minecraft/mc-mods/i18nupdatemod)
[![Modrinth](https://img.shields.io/modrinth/dt/PWERr14M?label=&logo=Modrinth&labelColor=white&color=00AF5C&style=for-the-badge)](https://modrinth.com/mod/i18nupdatemod)
[![License](https://img.shields.io/github/license/ChouChiu/I18nAutoUpdateMod?label=&logo=c&style=for-the-badge&color=A8B9CC&labelColor=455A64)](https://github.com/ChouChiu/I18nAutoUpdateMod/blob/main/LICENSE)
[![Build](https://img.shields.io/github/actions/workflow/status/ChouChiu/I18nAutoUpdateMod/beta.yml?style=for-the-badge&label=&logo=Gradle&labelColor=388E3C)](https://github.com/ChouChiu/I18nAutoUpdateMod/actions)

一个自动下载、更新、合并并应用「[简体中文资源包（Minecraft Mod Language Package）](https://github.com/CFPAOrg/Minecraft-Mod-Language-Package)」的客户端 Mod。

资源包更新在后台线程执行，不会等待网络下载而阻塞游戏启动。已有缓存会继续用于本次启动；后台更新完成的新资源包最迟在下次启动时生效。

## 下载

- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/i18nupdatemod)
- [Modrinth](https://modrinth.com/mod/i18nupdatemod)
- [GitHub Releases](https://github.com/ChouChiu/I18nAutoUpdateMod/releases)

## 支持范围

- Minecraft：1.6.1–1.21.11
- Mod Loader：MinecraftForge、NeoForge、Fabric、Quilt
- Java：8–21

将同一个 JAR 放入 `mods` 文件夹即可。Mod 会根据 Minecraft 版本与 Loader 选择合适的官方资源包，必要时合并多个资源包并转换 `pack.mcmeta`。

## 资源包来源

资源包优先从 CFPA 的固定 `autobuild` Release、CFPA 下载服务及可用镜像直接获取，不再依赖版本 Index。海外网络优先 GitHub，中国大陆网络会探测国内源并保留其他来源作为回退。

当前使用的官方资源版本包括 1.10.2、1.12.2、1.16、1.18、1.19、1.20 和 1.21。

## 开发

使用 JDK 21 构建，产物仍以 Java 8 为目标：

```shell
./gradlew clean test shadowJar
```

项目采用 [AGPL-3.0-only](LICENSE) 许可证。原作者为 xfl03，当前由 ChouChiu 维护。
