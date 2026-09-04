# SaveMySaves —— MC 1.16.5 自动备份 Mod

> 在游玩某个存档时，每隔一段时间自动把该存档压缩成 `.zip` 放到备份目录；滚动保留最多 N 份；可通过命令立即备份、查看状态。

> **分支说明（mc1165）**：由 `main`（1.12.2）移植。1.16.5 特有差异：
> - `Configuration` → `ForgeConfigSpec`（`config/savemysaves-common.toml`，注释全英文）
> - 1.16.5 双总线规则：MOD 总线（CommonSetup/ModConfigEvent）要求注册对象的所有 `@SubscribeEvent` 方法都是 `IModBusEvent`，生命周期监听独立成 `SaveMySaves.LifecycleHandler`；游戏事件在 FORGE 总线（`GameEvents`）
> - 命令 → Brigadier（`RegisterCommandsEvent`），`/backup` + 别名 `/sms`
> - 聊天组件 `TextComponent`（official 映射）；1.16.5 中 `FMLServerStoppedEvent` 在 FORGE 总线（字节码验证）
> - 世界根目录：`ISaveHandler` 已被 `LevelStorageAccess` 取代，统一走 `server.getWorldPath(LevelResource.ROOT)`
> - 语言文件 → JSON（`en_us.json`/`zh_cn.json`），服务端 Lang 类用 Gson 解析后纯文本下发
> - `NetworkCheckHandler` 已移除，改用 `mods.toml` 的 `displayTest="IGNORE_ALL_VERSION"`

## 环境
- Minecraft 1.16.5
- Forge 1.16.5 (36.2.39)
- ForgeGradle 5.1.77 + Gradle 7.3.3 + **JDK 8**（构建工具链，运行需 Java 8+）

## 构建
```bash
# FG 5.1.77 的 reobfJar 对 official 名字节码零匹配（已知名理），
# 因此构建分两步：
gradle build            # 产物 build/libs/savemysaves-1.16.5-1.0.0.jar（official 名 dev 版）
# 手工重映射为 SRG 发布版：
java -cp SpecialSource-1.11.0-shaded.jar;<mapped_official_jar> \
  net.md_5.specialsource.SpecialSource \
  --in-jar build/libs/savemysaves-1.16.5-1.0.0.jar \
  --out-jar savemysaves-1.16.5-1.0.0-srg.jar \
  --srg-in official_to_srg.tsrg --live
```
发布版（SRG 名）丢进 `.minecraft/mods/` 即可，`mods.toml` 使用 `${file.jarVersion}` 读取版本。

## 使用
1. 启动 MC，进入任意单人存档（或开局域网世界）
2. 备份只由**定时**（默认每 15 分钟）与**手动**触发
3. 备份文件位于：`saves/<你的存档名>/_backups/<存档名>_yyyy-MM-dd_HH-mm-ss.zip`
4. 命令：`/backup now`（立即备份）、`/backup status`（状态）、别名 `/sms`

## 配置（config/savemysaves-common.toml，注释全英文）
| 键 | 默认 | 说明 |
|---|---|---|
| `enabled` | true | 是否启用 |
| `intervalMinutes` | 15 | 定时备份间隔（分钟，1~1440） |
| `maxBackups` | 10 | 最多保留份数（滚动删除最旧） |
| `backupDirName` | `_backups` | 备份目录名（下划线开头避免被 MC 识别为存档） |
| `language` | `zh_CN` | 聊天/命令提示语言（服务端统一渲染）：`zh_CN` / `en_US` |

## 服务器使用
✅ 可以。服务端侧工具，玩家端**不需要**装（`displayTest="IGNORE_ALL_VERSION"`）。备份落在 `<服务器目录>/<世界名>/_backups/`。

---
License: MIT
