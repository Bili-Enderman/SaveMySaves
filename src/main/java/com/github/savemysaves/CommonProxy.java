package com.github.savemysaves;

import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.io.File;

/**
 * 公共代理：通过 TickEvent.WorldTick 检测"当前正在游玩哪个世界"，
 * 把对应 level 目录暴露给备份调度器。
 */
public class CommonProxy {

    protected SaveMySaves mod;

    /** 供 Forge @SidedProxy 反射实例化 */
    public CommonProxy() {
        this(null);
    }

    public CommonProxy(SaveMySaves mod) {
        this.mod = mod;
    }

    public void init(SaveMySaves mod) {
        // 注册自身到 Forge 事件总线
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
    }

    /** 每个世界 tick 触发（服务端 world）。在这里拿到世界的存档目录。 */
    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (event.world.isRemote) return; // 只处理服务端（单人游戏的服务端也在本地）

        File worldDir = getWorldDirectory(event.world);
        if (worldDir != null && worldDir.exists() && !worldDir.equals(SaveMySaves.currentWorldDir)) {
            // 进入新世界：标记当前世界（备份只由定时 / 手动 /backup now 触发）
            SaveMySaves.currentWorldDir = worldDir;
            SaveMySaves.inGameWorld = true;
        }
    }

    /**
     * 玩家登出/世界卸载：清空当前世界标记。
     * 仅单人游戏（含局域网主机）生效——FMLCommonHandler.getSide() 在单人侧返回 CLIENT，
     * 专用服务器返回 SERVER。专用服务器上玩家进进出出很频繁，
     * 若每个玩家登出都清标记会让定时备份失效；专用服务器由 onWorldTick 持续维护世界目录。
     */
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (FMLCommonHandler.instance().getSide() != Side.CLIENT) return;
        if (event.player != null && !event.player.world.isRemote) {
            SaveMySaves.currentWorldDir = null;
            SaveMySaves.inGameWorld = false;
        }
    }

    /**
     * 根据 World 取到其对应的世界根目录（saves/<name>）。
     * getSaveHandler().getWorldDirectory() 返回的是该维度的目录：
     *   - 主世界（Overworld）直接就是世界根目录
     *   - 下界(DIM-1)、末地(DIM1) 是其子目录，取 parent 即为根
     * 由于 tick 从主世界开始，这里统一返回 "维度目录的父目录"，
     * 对主世界而言 parent 也恰好指向 saves/<name>（因为主世界目录名 = 存档名）。
     */
    private File getWorldDirectory(net.minecraft.world.World world) {
        try {
            net.minecraft.world.storage.ISaveHandler sh = world.getSaveHandler();
            File dir = sh.getWorldDirectory();
            if (dir == null) return null;
            // 若 dir 名字是 DIM-1 / DIM1 这种维度目录，其父目录才是世界根
            String name = dir.getName();
            if ("DIM-1".equals(name) || "DIM1".equals(name)) {
                File parent = dir.getParentFile();
                return parent != null ? parent : dir;
            }
            return dir;
        } catch (Exception e) {
            BackupScheduler.log("[CommonProxy] 无法获取世界目录: " + e);
            return null;
        }
    }
}
