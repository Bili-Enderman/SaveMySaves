package com.github.savemysaves;

/**
 * 客户端代理（单人游戏下 tick 也会触发，用于确保 currentWorldDir 被正确设置）。
 * Forge 通过反射实例化，必须有无参构造函数。
 */
public class ClientProxy extends CommonProxy {

    /** 供 Forge 反射调用的无参构造 */
    public ClientProxy() {
        super(null);
    }

    // 事件注册、世界目录解析均复用父类 CommonProxy 逻辑，此处无需额外代码。
    // 注意：@SidedProxy 两侧代理只会被实例化"当前环境对应的一侧"，
    // 因此 CommonProxy.init() 中的事件注册不会重复。
}
