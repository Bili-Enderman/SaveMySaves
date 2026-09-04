# SaveMySaves —— MC 1.7.10 自动备份 Mod（仅源码）

> 在游玩某个存档时，每隔一段时间自动把该存档压缩成 `.zip` 放到备份目录；滚动保留最多 N 份；可通过命令立即备份、查看状态。

> **分支说明（mc1710）**：由 `main`（1.12.2）移植。1.7.10 特有差异：FML 类在 `cpw.mods.fml.*` 包下；事件总线未合并，游戏事件需同时注册 `MinecraftForge.EVENT_BUS` 与 `FMLCommonHandler.instance().bus()`；命令 API 为 `getCommandName/getCommandUsage/processCommand(sender, args)`；聊天组件为 `ChatComponentText/IChatComponent`。

## 功能
- ✅ **定时备份**：进入世界后，每隔 `intervalMinutes` 分钟自动备份当前正在游玩的存档
- ✅ **滚动保留**：每个世界独立保留最多 `maxBackups` 份，超出自动删除最旧的
- ✅ **立即备份**：`/backup now`
- ✅ **状态查询**：`/backup status`
- ✅ **安全**：自动跳过备份目录自身，避免旧 zip 被反复打进新 zip 导致无限膨胀
- ✅ **CFG 配置**：`config/savemysaves.cfg`，支持热读（重启生效）

## 环境
- Minecraft 1.7.10
- Forge 1.7.10 (10.13.4.1614，构建所用版本)
- **JDK 8**
- Gradle 2.14.1（ForgeGradle 1.2 要求 Gradle 2.x，wrapper 已指向腾讯镜像）

## 构建
```bash
# ForgeGradle 1.2 的 downloadClient/downloadServer 指向已关闭的 Mojang S3，
# build.gradle 已用 afterEvaluate 跳过；原版 jar 预置于
# ~/.gradle/caches/minecraft/net/minecraft/minecraft/1.7.10/minecraft-1.7.10.jar

# 2. 构建 jar
gradlew build
# 产物：build/libs/savemysaves-1710-1.0.0.jar
```
把 jar 丢进 `.minecraft/mods/` 即可。

> 注意：FG 1.2 无法用 Gradle 4.x+ 构建，请用 wrapper（2.14.1）或本机同版本 Gradle。

## 使用
1. 启动 MC，进入任意单人存档（或开局域网世界）
2. 备份只由**定时**（默认每 15 分钟）与**手动**触发，不会在进出世界时自动保存
3. 备份文件位于：`saves/<你的存档名>/_backups/<存档名>_yyyy-MM-dd_HH-mm-ss.zip`
4. 命令：
   - `/backup now` —— 立即备份
   - `/backup status` —— 查看启用状态、间隔、已备份份数、最近备份
   - 别名 `/sms`

## 服务器使用
✅ **可以**。本 Mod 是服务端侧工具，可装在专用服务器上，玩家端**不需要**装。

- **部署**：把 jar 放进服务端的 `mods/` 文件夹，启动即可
- **备份位置**：专用服务器上没有 `saves/`，备份落在 `<服务器目录>/<存档名>/_backups/`（默认存档名 `world`，即 `<服务器目录>/world/_backups/`）
- **定时/手动备份**：与单人一致，`/backup now`、`/backup status`、`/sms` 均可用；定时按 `intervalMinutes` 走（进出世界不会触发备份）
- **聊天本地化**：聊天/命令提示由**服务端统一渲染**后以纯文本下发（`config/savemysaves.cfg` → `general.language` 选 `zh_CN` 中文 / `en_US` 英文，默认中文），原版客户端、未装本模组的 Forge 客户端都能正确显示，不会出现 `savemysaves.*` 裸键名
- **权限**：命令等级 0，即 OP 与普通玩家都能执行；服务器上如需限制，可把 `getRequiredPermissionLevel()` 改为 1/2 后重新构建
- **注意**：备份会在玩家在线时压缩当前世界的 region 文件，可能存在轻微不一致（存档目录正在被写入）。建议在低活跃时段执行 `/backup now`，或把间隔调大到服务器空闲时段

## 配置（config/savemysaves.cfg）
| 键 | 默认 | 说明 |
|---|---|---|
| `enabled` | true | 是否启用 |
| `intervalMinutes` | 15 | 定时备份间隔（分钟，1~1440） |
| `maxBackups` | 10 | 最多保留份数（滚动删除最旧） |
| `backupDirName` | `_backups` | 备份目录名（下划线开头避免被 MC 识别为存档） |
| `language` | `zh_CN` | 聊天/命令提示语言：`zh_CN` 中文 / `en_US` 英文（服务端统一渲染） |

> 修改后**重启游戏**或执行 `/reload` 相关操作生效（Forge Configuration 在 preInit 重读）。

## 本地化
- 语言文件位于 `src/main/resources/assets/savemysaves/lang/`：`zh_CN.lang`（简体中文）、`en_US.lang`（English）
- 由于是服务端侧模组（客户端可不装），聊天/命令提示由服务端用 [Lang.java](src/main/java/com/github/savemysaves/Lang.java) 读取语言文件、`String.format` 格式化后以**纯文本**下发，不依赖客户端语言文件——原版/Forge 客户端都正确显示
- 语言由 `config/savemysaves.cfg` → `general.language` 选择（`zh_CN`/`en_US`，默认 `zh_CN`），英文作为缺键兜底
- 配置项名称/注释通过 `config.savemysaves.*` 键与 `config/savemysaves.cfg` 一一对应（Forge 配置 GUI 或未来扩展可显示本地化名称）

## 目录结构
```
savemysaves/
├── build.gradle
├── gradlew / gradlew.bat
├── gradle/wrapper/gradle-wrapper.properties
└── src/main/
    ├── java/com/github/savemysaves/
    │   ├── SaveMySaves.java       # @Mod 主类
    │   ├── Config.java             # CFG 配置
    │   ├── CommonProxy.java        # 服务端：监听世界 tick，标记当前存档
    │   ├── ClientProxy.java        # 客户端代理
    │   ├── BackupScheduler.java    # 后台调度：定时+立即+滚动删除
    │   ├── ZipUtil.java            # 目录压缩（排除备份目录）
    │   └── CommandBackup.java      # /backup 命令
    └── resources/
        ├── mcmod.info
        └── pack.mcmeta
```

## 关于"正在游玩的存档"的判定
通过 `TickEvent.WorldTickEvent`（服务端 world）拿到 `World.getSaveHandler().getWorldDirectory()`，
取其父目录即为 `saves/<存档名>`。仅当检测到有效世界目录时才触发备份，因此**不会备份未进入的存档**。

## 注意事项
- 备份在**独立后台线程**执行，不阻塞游戏主线程
- 使用 `AtomicBoolean backingUp` 防重入，同一时刻只会有一个备份任务
- 压缩为 zip 默认压缩级别 6（平衡速度与体积）
- 若存档很大，首次备份可能有短暂 IO 压力，属正常现象

---
License: MIT
