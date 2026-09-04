package com.github.savemysaves;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;

import java.io.File;

/**
 * /backup 命令（1.16.5 Brigadier 实现，替代 1.12.2 的 CommandBase）。
 * 用法：/backup <now|status>；别名 /sms 由 GameEvents 通过 redirect 注册。
 * 权限等级 0：单人游戏所有人可执行；服务器上如需限制可改 hasPermission 后重新构建。
 */
public class CommandBackup {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("backup")
                .requires(src -> src.hasPermission(0))
                .executes(ctx -> {
                    status(ctx.getSource());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("now")
                        .executes(ctx -> {
                            now(ctx.getSource());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("immediate")
                        .executes(ctx -> {
                            now(ctx.getSource());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("status")
                        .executes(ctx -> {
                            status(ctx.getSource());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("info")
                        .executes(ctx -> {
                            status(ctx.getSource());
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private static void now(CommandSourceStack source) {
        if (!Config.cfgEnabled) {
            source.sendFailure(BackupScheduler.text("savemysaves.chat.disabled"));
            return;
        }
        File worldDir = SaveMySaves.currentWorldDir;
        if (worldDir == null || !worldDir.exists()) {
            source.sendFailure(BackupScheduler.text("savemysaves.chat.noWorld"));
            return;
        }
        BackupScheduler.INSTANCE.requestImmediate("manual");
        source.sendSuccess(BackupScheduler.text("savemysaves.chat.requested"), false);
    }

    private static void status(CommandSourceStack source) {
        if (!Config.cfgEnabled) {
            source.sendFailure(BackupScheduler.text("savemysaves.chat.disabled"));
            return;
        }
        // 服务端渲染成纯文本（Lang），任何客户端都能显示，不会出现裸键名
        File worldDir = SaveMySaves.currentWorldDir;
        StringBuilder sb = new StringBuilder();
        sb.append(Lang.t("savemysaves.status.title")).append('\n');
        sb.append(Lang.t("savemysaves.status.enabled", Config.cfgEnabled)).append('\n');
        sb.append(Lang.t("savemysaves.status.interval", Config.cfgIntervalMinutes)).append('\n');
        sb.append(Lang.t("savemysaves.status.maxBackups", Config.cfgMaxBackups)).append('\n');
        if (worldDir != null && worldDir.exists()) {
            File backupDir = Config.getBackupDir(worldDir);
            File[] files = backupDir.listFiles((d, n) -> n.startsWith(worldDir.getName() + "_") && n.endsWith(".zip"));
            int count = files == null ? 0 : files.length;
            sb.append(Lang.t("savemysaves.status.world", worldDir.getName())).append('\n');
            sb.append(Lang.t("savemysaves.status.backupCount", count, Config.cfgMaxBackups)).append('\n');
            sb.append(Lang.t("savemysaves.status.backupDir", backupDir.getAbsolutePath()));
            if (count > 0 && files != null) {
                File newest = files[0];
                for (File f : files) if (f.lastModified() > newest.lastModified()) newest = f;
                sb.append('\n').append(Lang.t("savemysaves.status.lastBackup", newest.getName()));
            }
        } else {
            sb.append(Lang.t("savemysaves.status.noWorld"));
        }
        // 多行输出按行拆分逐条发送（聊天组件不渲染 \n）
        for (String line : sb.toString().split("\n")) {
            source.sendSuccess(new TextComponent(line), false);
        }
    }
}
