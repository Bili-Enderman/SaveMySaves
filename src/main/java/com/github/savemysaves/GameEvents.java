package com.github.savemysaves;

import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppedEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.io.File;

/**
 * FORGE 总线事件监听（替代 1.12.2 的 CommonProxy/ClientProxy）。
 * 1.16.5 事件总线已合并（1.8 起），游戏事件只在 MinecraftForge.EVENT_BUS 一条总线。
 */
public class GameEvents {

    /** 每个世界 tick 触发：维护"当前正在游玩的世界"的存档根目录。 */
    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (event.world.isClientSide || event.world.getServer() == null) return;

        // 1.16.5 差异：ISaveHandler 已被 LevelStorageAccess 取代。
        // 世界根目录统一走 server.getWorldPath(LevelResource.ROOT)：
        // 单人=saves/<名>，专用服务器=世界根目录，语义与 1.12.2 的解析逻辑一致。
        File worldDir = event.world.getServer().getWorldPath(LevelResource.ROOT).toFile();
        if (worldDir.exists() && !worldDir.equals(SaveMySaves.currentWorldDir)) {
            SaveMySaves.currentWorldDir = worldDir;
            SaveMySaves.inGameWorld = true;
        }
    }

    /**
     * 玩家登出：仅客户端侧（单人/局域网主机）清空当前世界标记——
     * 专用服务器玩家进出频繁，若每次登出都清标记会让定时备份失效（由 onWorldTick 持续维护）。
     * 1.16.5 差异：FMLCommonHandler.getSide() 已移除，dist 判断走 FMLEnvironment.dist；
     * Entity.world 字段在 official 映射下为 level。
     */
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        if (event.getPlayer() != null && !event.getPlayer().level.isClientSide) {
            SaveMySaves.currentWorldDir = null;
            SaveMySaves.inGameWorld = false;
        }
    }

    /** 服务端停止：清空当前世界标记（1.16.5 中 FMLServerStoppedEvent 在 FORGE 总线，字节码已验证）。 */
    @SubscribeEvent
    public void onServerStopped(FMLServerStoppedEvent event) {
        SaveMySaves.currentWorldDir = null;
        SaveMySaves.inGameWorld = false;
    }

    /** 注册 Brigadier 命令 /backup（别名 /sms），替代 1.12.2 的 registerServerCommand。 */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralCommandNode<CommandSourceStack> node = event.getDispatcher().register(CommandBackup.build());
        event.getDispatcher().register(Commands.literal("sms").redirect(node));
        SaveMySaves.LOGGER.info("Command '/backup' (alias '/sms') registered.");
    }
}
