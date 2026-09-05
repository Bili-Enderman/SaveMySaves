package com.github.savemysaves;

import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.io.File;

/**
 * 游戏事件监听（NeoForge.EVENT_BUS，替代 1.12.2 的 CommonProxy/ClientProxy）。
 * 1.20.6 差异：
 *   - MinecraftForge.EVENT_BUS → NeoForge.EVENT_BUS（net.neoforged.neoforge.common.NeoForge）
 *   - TickEvent.WorldTickEvent → LevelTickEvent.Pre/Post（1.20.5 起拆分，无 phase 字段）
 *   - FMLServerStoppedEvent（fml 包）→ ServerStoppedEvent（neoforge.event.server 包）
 *   - Entity.level 字段 → level() 访问器（1.20 起）
 */
public class GameEvents {

    /** 每个世界 tick 触发：维护"当前正在游玩的世界"的存档根目录。 */
    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        // 1.16.5 起世界根目录统一走 server.getWorldPath(LevelResource.ROOT)：
        // 单人=saves/<名>，专用服务器=世界根目录。
        // 注意：LevelResource.ROOT 的 id 是"."，返回路径带尾部"/."成分，
        // 必须 normalize()，否则 worldDir.getName() 变成"."，备份目录与文件名全错。
        File worldDir = serverLevel.getServer().getWorldPath(LevelResource.ROOT).normalize().toFile();
        if (worldDir.exists() && !worldDir.equals(SaveMySaves.currentWorldDir)) {
            SaveMySaves.currentWorldDir = worldDir;
            SaveMySaves.inGameWorld = true;
        }
    }

    /**
     * 玩家登出：仅客户端侧（单人/局域网主机）清空当前世界标记——
     * 专用服务器玩家进出频繁，若每次登出都清标记会让定时备份失效（由 onLevelTick 持续维护）。
     */
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        if (event.getEntity() != null && !event.getEntity().level().isClientSide) {
            SaveMySaves.currentWorldDir = null;
            SaveMySaves.inGameWorld = false;
        }
    }

    /** 服务端停止：清空当前世界标记。 */
    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        SaveMySaves.currentWorldDir = null;
        SaveMySaves.inGameWorld = false;
    }

    /** 注册 Brigadier 命令 /backup（别名 /sms）。 */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralCommandNode<CommandSourceStack> node = event.getDispatcher().register(CommandBackup.build());
        event.getDispatcher().register(Commands.literal("sms").redirect(node));
        SaveMySaves.LOGGER.info("Command '/backup' (alias '/sms') registered.");
    }
}
