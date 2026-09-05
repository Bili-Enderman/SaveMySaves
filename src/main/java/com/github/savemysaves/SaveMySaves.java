package com.github.savemysaves;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.io.File;

/**
 * SaveMySaves —— 1.20.6 NeoForge 移植版。
 *
 * 与 1.16.5 Forge 版本的主要差异：
 *   - 加载器：NeoForge 20.6（元数据 META-INF/neoforge.mods.toml，1.20.5 起 mods.toml 改名）
 *   - @Mod 构造器注入 IEventBus + ModContainer；配置经 container.registerConfig 注册
 *   - 配置：ForgeConfigSpec → ModConfigSpec（net.neoforged.neoforge.common）
 *   - 事件总线：MinecraftForge.EVENT_BUS → NeoForge.EVENT_BUS；FML 事件包 net.minecraftforge.fml → net.neoforged.fml
 *   - 日志：Log4j → SLF4J（com.mojang.logging.LogUtils）
 *   - 1.20.5 起 mod 不注册网络通道即不参与连接校验，无需 displayTest 也可原版客户端入服（此处仍保留）
 */
@Mod(SaveMySaves.MODID)
public class SaveMySaves {

    public static final String MODID = "savemysaves";
    public static final String NAME = "SaveMySaves";
    public static final String VERSION = "1.20.6-1.0.0";

    public static final Logger LOGGER = LogUtils.getLogger();

    /** 当前正在游玩的世界对应的存档根目录（单人=saves/<名>，专用服务器=世界目录），由 GameEvents 维护 */
    public static volatile File currentWorldDir;

    /** 是否当前处于"正在游玩某个世界"的状态 */
    public static volatile boolean inGameWorld;

    public SaveMySaves(IEventBus modEventBus, ModContainer modContainer) {
        // 注册配置文件（config/savemysaves-common.toml）
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // MOD 总线：生命周期与配置事件；游戏事件（NeoForge.EVENT_BUS）见 GameEvents
        modEventBus.register(new LifecycleHandler());
        NeoForge.EVENT_BUS.register(new GameEvents());

        LOGGER.info("SaveMySaves {} constructed.", VERSION);
    }

    /** MOD 总线监听：生命周期事件（不能与游戏事件混在同一个注册对象上） */
    public static class LifecycleHandler {

        /** 原逻辑在 postInit 启动后台调度线程，1.16.5 起在 commonSetup（NeoForge 20.6 同名保留） */
        @SubscribeEvent
        public void onCommonSetup(FMLCommonSetupEvent event) {
            BackupScheduler.INSTANCE.start();
        }

        @SubscribeEvent
        public void onModConfigEvent(ModConfigEvent event) {
            Config.refresh();
            Lang.load(Config.cfgLanguage);
            LOGGER.info("SaveMySaves config (re)loaded. enabled={}, interval={}min, maxBackups={}, lang={}",
                    Config.cfgEnabled, Config.cfgIntervalMinutes, Config.cfgMaxBackups, Config.cfgLanguage);
        }
    }
}
