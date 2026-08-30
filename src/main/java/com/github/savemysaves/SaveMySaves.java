package com.github.savemysaves;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.network.NetworkCheckHandler;
import net.minecraftforge.fml.relauncher.Side;

import java.io.File;
import java.util.Map;

@Mod(
        modid = SaveMySaves.MODID,
        name = SaveMySaves.NAME,
        version = SaveMySaves.VERSION,
        acceptableRemoteVersions = "*"   // 纯客户端/服务端工具，不强校验对端
)
public class SaveMySaves {

    public static final String MODID = "savemysaves";
    public static final String NAME = "SaveMySaves";
    public static final String VERSION = "1.0.0";

    @SidedProxy(
            clientSide = "com.github.savemysaves.ClientProxy",
            serverSide = "com.github.savemysaves.CommonProxy"
    )
    public static CommonProxy proxy;

    /** 配置对象（对外可读） */
    public static Configuration config;

    /** 当前正在游玩的世界对应的 saves 下子目录（即 level 目录），由 WorldEvent 监听填充 */
    public static volatile File currentWorldDir;

    /** Minecraft 根目录（runDir，通常含 saves/） */
    public static File mcBaseDir;

    /** 是否当前处于"单人游戏中正在游玩某个世界"的状态 */
    public static volatile boolean inGameWorld;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        mcBaseDir = event.getModConfigurationDirectory().getParentFile(); // .minecraft
        config = new Configuration(new File(event.getModConfigurationDirectory(), MODID + ".cfg"));
        Config.load(config);
        // 服务端侧语言包：聊天/命令提示由服务端渲染成纯文本再下发（见 Lang 注释）
        Lang.load(Config.LANGUAGE);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(this);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        // 启动后台调度线程（仅在实际游戏侧）
        BackupScheduler.INSTANCE.start();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandBackup());
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        // 停止/存档切换时清空当前世界标记
        currentWorldDir = null;
        inGameWorld = false;
    }

    /** 允许在单人/局域网客户端也执行命令 */
    @NetworkCheckHandler
    public boolean checkNetwork(Map<String, String> mods, Side side) {
        return true;
    }
}
