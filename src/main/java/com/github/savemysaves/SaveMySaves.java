package com.github.savemysaves;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

/**
 * SaveMySaves —— 1.16.5 Forge 移植版。
 *
 * 与 1.12.2 版本的主要差异：
 *   - Configuration → ForgeConfigSpec（见 Config，文件 config/savemysaves-common.toml）
 *   - SidedProxy → 直接注册事件总线；1.16.5 双总线规则：
 *       MOD 总线（CommonSetup/ModConfigEvent 等生命周期）要求注册对象的
 *       所有 @SubscribeEvent 方法都是 IModBusEvent，故拆出静态内部类 LifecycleHandler；
 *       游戏事件（WorldTick/PlayerLoggedOut/ServerStopped/RegisterCommands）在 FORGE 总线（见 GameEvents）
 *   - FMLServerStartingEvent.registerServerCommand → Brigadier（RegisterCommandsEvent）
 *   - NetworkCheckHandler 已不存在，改用 mods.toml 的 displayTest="IGNORE_ALL_VERSION"
 */
@Mod(SaveMySaves.MODID)
public class SaveMySaves {

    public static final String MODID = "savemysaves";
    public static final String NAME = "SaveMySaves";
    public static final String VERSION = "1.0.0";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    /** 当前正在游玩的世界对应的存档根目录（单人=saves/<名>，专用服务器=世界目录），由 GameEvents 维护 */
    public static volatile File currentWorldDir;

    /** 是否当前处于"正在游玩某个世界"的状态 */
    public static volatile boolean inGameWorld;

    public SaveMySaves() {
        // 注册配置文件（config/savemysaves-common.toml）
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // MOD 总线：生命周期；FORGE 总线：游戏事件。两条总线不可混注册。
        FMLJavaModLoadingContext.get().getModEventBus().register(new LifecycleHandler());
        MinecraftForge.EVENT_BUS.register(new GameEvents());

        LOGGER.info("SaveMySaves {} constructed.", VERSION);
    }

    /** MOD 总线监听：生命周期事件（不能与 FORGE 总线事件混在同一个注册对象上） */
    public static class LifecycleHandler {

        /** 原逻辑在 postInit 启动后台调度线程，1.16.5 生命周期事件在 commonSetup */
        @SubscribeEvent
        public void onCommonSetup(FMLCommonSetupEvent event) {
            BackupScheduler.INSTANCE.start();
        }

        @SubscribeEvent
        public void onModConfigEvent(ModConfig.ModConfigEvent event) {
            Config.refresh();
            Lang.load(Config.cfgLanguage);
            LOGGER.info("SaveMySaves config (re)loaded. enabled={}, interval={}min, maxBackups={}, lang={}",
                    Config.cfgEnabled, Config.cfgIntervalMinutes, Config.cfgMaxBackups, Config.cfgLanguage);
        }
    }
}
