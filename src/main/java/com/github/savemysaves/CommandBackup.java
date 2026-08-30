package com.github.savemysaves;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class CommandBackup extends CommandBase {

    @Override
    public String getName() {
        return "backup";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/backup <now|status>";
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("sms");
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0; // 单人游戏所有人可执行；服务端可结合 OP 权限
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (!Config.ENABLED) {
            send(sender, "savemysaves.chat.disabled");
            return;
        }

        String sub = args.length > 0 ? args[0].toLowerCase() : "status";

        if ("now".equals(sub) || "immediate".equals(sub)) {
            File worldDir = SaveMySaves.currentWorldDir;
            if (worldDir == null || !worldDir.exists()) {
                send(sender, "savemysaves.chat.noWorld");
                return;
            }
            BackupScheduler.INSTANCE.requestImmediate("manual");
            send(sender, "savemysaves.chat.requested");
        } else if ("status".equals(sub) || "info".equals(sub)) {
            // 服务端渲染成纯文本（Lang），任何客户端都能显示，不会出现裸键名
            File worldDir = SaveMySaves.currentWorldDir;
            StringBuilder sb = new StringBuilder();
            sb.append(Lang.t("savemysaves.status.title")).append('\n');
            sb.append(Lang.t("savemysaves.status.enabled", Config.ENABLED)).append('\n');
            sb.append(Lang.t("savemysaves.status.interval", Config.INTERVAL_MINUTES)).append('\n');
            sb.append(Lang.t("savemysaves.status.maxBackups", Config.MAX_BACKUPS)).append('\n');
            if (worldDir != null && worldDir.exists()) {
                File backupDir = Config.getBackupDir(worldDir);
                File[] files = backupDir.listFiles((d, n) -> n.startsWith(worldDir.getName() + "_") && n.endsWith(".zip"));
                int count = files == null ? 0 : files.length;
                sb.append(Lang.t("savemysaves.status.world", worldDir.getName())).append('\n');
                sb.append(Lang.t("savemysaves.status.backupCount", count, Config.MAX_BACKUPS)).append('\n');
                sb.append(Lang.t("savemysaves.status.backupDir", backupDir.getAbsolutePath()));
                if (count > 0 && files != null) {
                    File newest = files[0];
                    for (File f : files) if (f.lastModified() > newest.lastModified()) newest = f;
                    sb.append('\n').append(Lang.t("savemysaves.status.lastBackup", newest.getName()));
                }
            } else {
                sb.append(Lang.t("savemysaves.status.noWorld"));
            }
            send(sender, new TextComponentString(sb.toString()));
        } else {
            send(sender, "savemysaves.chat.usage", getUsage(sender));
        }
    }

    private void send(ICommandSender sender, String key, Object... args) {
        sender.sendMessage(BackupScheduler.text(key, args));
    }

    private void send(ICommandSender sender, ITextComponent msg) {
        sender.sendMessage(msg);
    }
}
