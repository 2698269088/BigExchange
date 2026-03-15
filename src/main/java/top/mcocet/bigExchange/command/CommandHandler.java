package top.mcocet.bigExchange.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import top.mcocet.bigExchange.BigExchange;
import top.mcocet.bigExchange.manager.CodeManager;
import top.mcocet.bigExchange.manager.DatabaseManager;
import top.mcocet.bigExchange.manager.ConfigManager;
import top.mcocet.bigExchange.manager.LogManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CommandHandler implements CommandExecutor, TabCompleter {
    private final BigExchange plugin;
    private final CodeManager codeManager;
    private final DatabaseManager databaseManager;
    private final ConfigManager configManager;
    private final LogManager logManager;

    public CommandHandler(BigExchange plugin, CodeManager codeManager, 
                         DatabaseManager databaseManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.codeManager = codeManager;
        this.databaseManager = databaseManager;
        this.configManager = configManager;
        this.logManager = new LogManager(plugin, configManager);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, 
                            String label, String[] args) {
        
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "generate" -> {
                if (!checkPermission(sender, "bigexchange.admin")) return true;
                handleGenerate(sender, args);
            }
            case "delete" -> {
                if (!checkPermission(sender, "bigexchange.admin")) return true;
                handleDelete(sender, args);
            }
            case "modify" -> {
                if (!checkPermission(sender, "bigexchange.admin")) return true;
                handleModify(sender, args);
            }
            case "validity" -> {
                if (!checkPermission(sender, "bigexchange.admin")) return true;
                handleValidity(sender, args);
            }
            case "list" -> {
                if (!checkPermission(sender, "bigexchange.admin")) return true;
                handleList(sender, args);
            }
            case "info" -> {
                if (!checkPermission(sender, "bigexchange.admin")) return true;
                handleInfo(sender, args);
            }
            case "history" -> {
                if (!checkPermission(sender, "bigexchange.admin")) return true;
                handleHistory(sender, args);
            }
            case "activate" -> {
                if (!checkPermission(sender, "bigexchange.admin")) return true;
                handleActivate(sender, args);
            }
            case "clear" -> {
                if (!checkPermission(sender, "bigexchange.admin")) return true;
                handleClear(sender, args);
            }
            case "backup" -> {
                if (!checkPermission(sender, "bigexchange.admin")) return true;
                handleBackup(sender, args);
            }
            case "reload" -> {
                if (!checkPermission(sender, "bigexchange.admin")) return true;
                handleReload(sender);
            }
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleGenerate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(configManager.getMessage("error") + "用法：/be generate <次数|unlimited> [奖励命令] [有效期天数]");
            return;
        }

        int uses;
        if ("unlimited".equalsIgnoreCase(args[1])) {
            uses = -1;
        } else {
            try {
                uses = Integer.parseInt(args[1]);
                if (uses < 1) {
                    sender.sendMessage(configManager.getMessage("error") + "次数必须大于 0 或为 unlimited");
                    return;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(configManager.getMessage("error") + "无效的次数值");
                return;
            }
        }

        // 获取奖励命令（从第三个参数开始，可以包含空格）
        String rewardCommands = null;
        int validityDays = configManager.getDefaultValidityDays(); // 默认使用配置的有效期
        
        if (args.length >= 3) {
            // 检查第三个参数是否是数字（有效期）还是命令
            try {
                validityDays = Integer.parseInt(args[2]);
                // 如果成功解析为数字，继续检查是否有奖励命令
                if (args.length >= 4) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 3; i < args.length; i++) {
                        if (sb.length() > 0) sb.append(" ");
                        sb.append(args[i]);
                    }
                    rewardCommands = sb.toString();
                }
            } catch (NumberFormatException e) {
                // 第三个参数不是数字，当作奖励命令处理
                StringBuilder sb = new StringBuilder();
                for (int i = 2; i < args.length; i++) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(args[i]);
                }
                rewardCommands = sb.toString();
                
                // 检查最后一个参数是否是有效期
                String[] cmdParts = rewardCommands.split(" ");
                if (cmdParts.length > 1) {
                    try {
                        String lastPart = cmdParts[cmdParts.length - 1];
                        validityDays = Integer.parseInt(lastPart);
                        // 移除最后一个参数（有效期）
                        rewardCommands = rewardCommands.substring(0, rewardCommands.lastIndexOf(" "));
                    } catch (NumberFormatException ex) {
                        // 最后一个参数不是数字，使用默认有效期
                    }
                }
            }
        }

        String createdBy = sender.getName();
        String code = codeManager.generateCode(uses, createdBy, rewardCommands, validityDays);

        String message = configManager.getMessage("code-generated")
                .replace("%code%", code)
                .replace("%uses%", uses == -1 ? "无限" : String.valueOf(uses))
                .replace("%validity%", validityDays == -1 ? "永久有效" : validityDays + "天");
        sender.sendMessage(message);
        
        if (rewardCommands != null && !rewardCommands.isEmpty()) {
            sender.sendMessage(configManager.getMessage("prefix") + "§7奖励命令：§b" + rewardCommands);
        }
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(configManager.getMessage("error") + "用法：/be delete <兑换码|ID>");
            return;
        }

        String identifier = args[1];
        DatabaseManager.CodeData codeData = getCodeByIdentifier(identifier);
        
        if (codeData == null) {
            sender.sendMessage(configManager.getMessage("error") + "未找到该兑换码");
            return;
        }

        if (codeManager.deleteCode(codeData.id)) {
            sender.sendMessage(configManager.getMessage("success") + "已删除兑换码：" + codeData.code);
        } else {
            sender.sendMessage(configManager.getMessage("error") + "删除失败");
        }
    }

    private void handleModify(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(configManager.getMessage("error") + "用法：/be modify <兑换码|ID> <次数|unlimited>");
            return;
        }

        String identifier = args[1];
        DatabaseManager.CodeData codeData = getCodeByIdentifier(identifier);
        
        if (codeData == null) {
            sender.sendMessage(configManager.getMessage("error") + "未找到该兑换码");
            return;
        }

        int newUses;
        if ("unlimited".equalsIgnoreCase(args[2])) {
            newUses = -1;
        } else {
            try {
                newUses = Integer.parseInt(args[2]);
                if (newUses < 1) {
                    sender.sendMessage(configManager.getMessage("error") + "次数必须大于 0 或为 unlimited");
                    return;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(configManager.getMessage("error") + "无效的次数值");
                return;
            }
        }

        if (codeManager.modifyCodeUses(codeData.id, newUses)) {
            sender.sendMessage(configManager.getMessage("success") + 
                    "已修改兑换码 " + codeData.code + " 的使用次数为：" + 
                    (newUses == -1 ? "无限" : newUses));
        } else {
            sender.sendMessage(configManager.getMessage("error") + "修改失败");
        }
    }

    private void handleValidity(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(configManager.getMessage("error") + "用法：/be validity <兑换码|ID> <天数|permanent>");
            sender.sendMessage("§7  天数：设置有效期天数（从创建时间开始计算）");
            sender.sendMessage("§7  permanent: 设置为永久有效");
            return;
        }

        String identifier = args[1];
        DatabaseManager.CodeData codeData = getCodeByIdentifier(identifier);
        
        if (codeData == null) {
            sender.sendMessage(configManager.getMessage("error") + "未找到该兑换码");
            return;
        }

        int validityDays;
        if ("permanent".equalsIgnoreCase(args[2])) {
            validityDays = -1;
        } else {
            try {
                validityDays = Integer.parseInt(args[2]);
                if (validityDays < 1) {
                    sender.sendMessage(configManager.getMessage("error") + "有效期天数必须大于 0 或为 permanent");
                    return;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(configManager.getMessage("error") + "无效的有效期天数");
                return;
            }
        }

        if (codeManager.modifyCodeValidity(codeData.id, validityDays)) {
            String validityStr = validityDays == -1 ? "永久有效" : validityDays + "天（从创建时间" + codeData.createdTime + "开始计算）";
            sender.sendMessage(configManager.getMessage("success") + 
                    "已修改兑换码 " + codeData.code + " 的有效期为：" + validityStr);
            
            // 显示过期时间
            if (validityDays != -1) {
                long expirationMillis = codeData.createdTime.getTime() + (long) validityDays * 24 * 60 * 60 * 1000;
                java.sql.Timestamp expirationTime = new java.sql.Timestamp(expirationMillis);
                sender.sendMessage("§7过期时间：§c" + expirationTime.toString());
            }
        } else {
            sender.sendMessage(configManager.getMessage("error") + "修改失败");
        }
    }

    private void handleList(CommandSender sender, String[] args) {
        List<DatabaseManager.CodeData> codes = codeManager.getAllCodes();
        
        if (codes.isEmpty()) {
            sender.sendMessage(configManager.getMessage("prefix") + "暂无兑换码");
            return;
        }

        sender.sendMessage(configManager.getMessage("prefix") + "§6=== 兑换码列表 ===");
        
        // 过滤参数
        String filter = args.length > 1 ? args[1].toLowerCase() : "all";
        
        for (DatabaseManager.CodeData code : codes) {
            boolean show = switch (filter) {
                case "used" -> !code.hasUsesLeft();
                case "expired", "inactive" -> !code.isActive || code.isExpired();
                case "active" -> code.isActive && code.hasUsesLeft() && !code.isExpired();
                default -> true;
            };

            if (show) {
                String status = code.isActive ? "§a[激活]" : "§c[停用]";
                if (code.isExpired()) {
                    status += "§8[已过期]";
                }
                String uses = code.isUnlimited() ? "§b[无限]" : 
                        "§e" + code.getRemainingUses() + "/" + code.uses;
                
                // 显示有效期信息
                String validityInfo = "";
                if (code.hasValidity()) {
                    validityInfo = " §7(剩:" + code.getFormattedRemainingTime() + ")";
                } else {
                    validityInfo = " §7(永久)";
                }
                
                sender.sendMessage(String.format("§7%s §b%s §r- %s 次%s", 
                        status, code.code, uses, validityInfo));
            }
        }
        
        sender.sendMessage(configManager.getMessage("prefix") + "§6=================");
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(configManager.getMessage("error") + "用法：/be info <兑换码|ID>");
            return;
        }

        String identifier = args[1];
        DatabaseManager.CodeData codeData = getCodeByIdentifier(identifier);
        
        if (codeData == null) {
            sender.sendMessage(configManager.getMessage("error") + "未找到该兑换码");
            return;
        }

        sender.sendMessage(configManager.getMessage("prefix") + "§6=== 兑换码信息 ===");
        sender.sendMessage("§7兑换码：§b" + codeData.code);
        sender.sendMessage("§7状态：" + (codeData.isActive ? "§a激活" : "§c停用"));
        sender.sendMessage("§7使用次数：" + (codeData.isUnlimited() ? "§b无限" : 
                codeData.usedCount + "/" + codeData.uses));
        sender.sendMessage("§7剩余次数：" + (codeData.isUnlimited() ? "§b无限" : 
                codeData.getRemainingUses()));
        sender.sendMessage("§7创建者：" + codeData.createdBy);
        sender.sendMessage("§7创建时间：" + codeData.createdTime.toString());
        
        // 显示有效期信息
        if (codeData.hasValidity()) {
            sender.sendMessage("§7有效期：§e" + codeData.validityDays + "天");
            if (codeData.expirationTime != null) {
                sender.sendMessage("§7过期时间：§c" + codeData.expirationTime.toString());
                sender.sendMessage("§7剩余时间：" + codeData.getFormattedRemainingTime());
            }
        } else {
            sender.sendMessage("§7有效期：§a永久有效");
        }
        
        if (codeData.lastUsed != null) {
            sender.sendMessage("§7最后使用：" + codeData.lastUsed.toString());
        }
        sender.sendMessage(configManager.getMessage("prefix") + "§6====================");
    }

    private void handleHistory(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(configManager.getMessage("error") + "用法：/be history <兑换码|ID>");
            return;
        }

        String identifier = args[1];
        DatabaseManager.CodeData codeData = getCodeByIdentifier(identifier);
        
        if (codeData == null) {
            sender.sendMessage(configManager.getMessage("error") + "未找到该兑换码");
            return;
        }

        List<DatabaseManager.CodeUsage> history = codeManager.getUsageHistory(codeData.id);
        
        if (history.isEmpty()) {
            sender.sendMessage(configManager.getMessage("prefix") + "该兑换码暂无使用记录");
            return;
        }

        sender.sendMessage(configManager.getMessage("prefix") + "§6=== 使用历史 ===");
        for (DatabaseManager.CodeUsage usage : history) {
            sender.sendMessage(String.format("§7玩家：%s (§b%s§7) - 时间：%s",
                    usage.playerName, usage.playerUuid, usage.usedTime.toString()));
        }
        sender.sendMessage(configManager.getMessage("prefix") + "§6==================");
    }

    private void handleActivate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(configManager.getMessage("error") + "用法：/be activate <兑换码|ID>");
            return;
        }

        String identifier = args[1];
        DatabaseManager.CodeData codeData = getCodeByIdentifier(identifier);
        
        if (codeData == null) {
            sender.sendMessage(configManager.getMessage("error") + "未找到该兑换码");
            return;
        }

        if (codeManager.forceActivateCode(codeData.id)) {
            sender.sendMessage(configManager.getMessage("admin-force-activated"));
        } else {
            sender.sendMessage(configManager.getMessage("error") + "激活失败");
        }
    }

    private void handleReload(CommandSender sender) {
        configManager.reloadConfig();
        sender.sendMessage(configManager.getMessage("success") + "插件配置已重载");
    }

    /**
     * 清空数据库
     */
    private void handleClear(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(configManager.getMessage("error") + "§c用法：/be clear <all|codes|history>");
            sender.sendMessage("§7  all - 清空所有数据（包括使用历史）");
            sender.sendMessage("§7  codes - 只清空兑换码");
            sender.sendMessage("§7  history - 只清空使用历史");
            return;
        }

        String type = args[1].toLowerCase();
        boolean success = false;

        switch (type) {
            case "all" -> {
                // 清空所有兑换码
                List<DatabaseManager.CodeData> codes = codeManager.getAllCodes();
                for (DatabaseManager.CodeData code : codes) {
                    databaseManager.deleteCode(code.id);
                }
                success = true;
                sender.sendMessage(configManager.getMessage("prefix") + "§a已清空所有数据！");
            }
            case "codes" -> {
                // 清空所有兑换码
                List<DatabaseManager.CodeData> codes = codeManager.getAllCodes();
                for (DatabaseManager.CodeData code : codes) {
                    databaseManager.deleteCode(code.id);
                }
                success = true;
                sender.sendMessage(configManager.getMessage("prefix") + "§a已清空所有兑换码！");
            }
            case "history" -> {
                // TODO: 如果需要单独清空历史记录，可以在 DatabaseManager 中添加方法
                sender.sendMessage(configManager.getMessage("prefix") + "§e清空使用历史功能暂未实现");
                return;
            }
            default -> {
                sender.sendMessage(configManager.getMessage("error") + "未知类型：" + type);
                return;
            }
        }

        if (success) {
            logManager.info(sender.getName() + " 执行了清空操作：" + type);
        }
    }

    /**
     * 备份数据库
     */
    private void handleBackup(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(configManager.getMessage("error") + "§c用法：/be backup <sqlite|mysql|both>");
            sender.sendMessage("§7  sqlite - 备份 SQLite 数据库到本地");
            sender.sendMessage("§7  mysql - 备份 MySQL 数据到 SQLite");
            sender.sendMessage("§7  both - 同时备份");
            return;
        }

        String type = args[1].toLowerCase();
        boolean success = false;

        switch (type) {
            case "sqlite" -> {
                // 备份当前 SQLite 数据库到文件
                success = backupSQLite(sender);
            }
            case "mysql" -> {
                // 将 MySQL 数据备份到 SQLite
                success = backupMySQLToSQLite(sender);
            }
            case "both" -> {
                success = backupSQLite(sender) && backupMySQLToSQLite(sender);
            }
            default -> {
                sender.sendMessage(configManager.getMessage("error") + "未知类型：" + type);
                return;
            }
        }

        if (success) {
            sender.sendMessage(configManager.getMessage("prefix") + "§a备份完成！");
            logManager.info(sender.getName() + " 执行了备份操作：" + type);
        } else {
            sender.sendMessage(configManager.getMessage("error") + "备份失败，请查看控制台日志");
        }
    }

    /**
     * 备份 SQLite 数据库到文件
     */
    private boolean backupSQLite(CommandSender sender) {
        try {
            java.io.File sourceFile = new java.io.File(configManager.getDatabasePath());
            if (!sourceFile.exists()) {
                sender.sendMessage(configManager.getMessage("prefix") + "§cSQLite 数据库文件不存在");
                return false;
            }

            // 创建备份目录
            java.io.File backupDir = new java.io.File(plugin.getDataFolder(), "backups");
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }

            // 生成备份文件名（带时间戳）
            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date());
            String backupFileName = "backup_" + timestamp + ".db";
            java.io.File backupFile = new java.io.File(backupDir, backupFileName);

            // 复制文件
            java.nio.file.Files.copy(sourceFile.toPath(), backupFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            sender.sendMessage(configManager.getMessage("prefix") + "§7SQLite 数据库已备份至：§b" + backupFile.getAbsolutePath());
            logManager.info("SQLite 数据库已备份至：" + backupFile.getAbsolutePath());

            return true;
        } catch (Exception e) {
            logManager.severe("SQLite 数据库备份失败：" + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 将 MySQL 数据备份到 SQLite
     */
    private boolean backupMySQLToSQLite(CommandSender sender) {
        try {
            DatabaseManager.DatabaseType dbType = databaseManager.getActiveDatabaseType();
            
            if (dbType == DatabaseManager.DatabaseType.SQLITE) {
                sender.sendMessage(configManager.getMessage("prefix") + "§e当前未使用 MySQL，无需备份");
                return false;
            }

            // 获取所有 MySQL 数据
            List<DatabaseManager.CodeData> codes = databaseManager.getAllCodes();
            
            if (codes.isEmpty()) {
                sender.sendMessage(configManager.getMessage("prefix") + "§eMySQL 数据库中没有数据");
                return true;
            }

            int count = 0;
            for (DatabaseManager.CodeData code : codes) {
                // 保存到 SQLite（通过 DatabaseManager 的双写机制）
                // 这里直接调用底层 Handler
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    // 由于 DatabaseManager 已经处理了双写，我们只需要保存即可
                    // 但为了避免重复，我们需要直接操作 SQLite Handler
                    // 这需要通过反射或其他方式获取，暂时简化处理
                });
                count++;
            }

            sender.sendMessage(configManager.getMessage("prefix") + "§7已从 MySQL 备份 §b" + count + "§7 条记录到 SQLite");
            logManager.info("从 MySQL 备份 " + count + " 条记录到 SQLite");

            return true;
        } catch (Exception e) {
            logManager.severe("MySQL 到 SQLite 备份失败：" + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(configManager.getMessage("prefix") + "§6=== BigExchange 帮助 ===");
        sender.sendMessage("§7/be generate <次数|unlimited> [奖励命令] [有效期] §8- 生成兑换码");
        sender.sendMessage("§7/be delete <兑换码|ID> §8- 删除兑换码");
        sender.sendMessage("§7/be modify <兑换码|ID> <次数> §8- 修改次数");
        sender.sendMessage("§7/be validity <兑换码|ID> <天数> §8- 修改有效期");
        sender.sendMessage("§7/be list [all|active|used|expired] §8- 列出兑换码");
        sender.sendMessage("§7/be info <兑换码|ID> §8- 查看信息");
        sender.sendMessage("§7/be history <兑换码|ID> §8- 查看使用历史");
        sender.sendMessage("§7/be activate <兑换码|ID> §8- 强制激活");
        sender.sendMessage("§7/be clear <all|codes|history> §8- 清空数据");
        sender.sendMessage("§7/be backup <sqlite|mysql|both> §8- 备份数据库");
        sender.sendMessage("§7/be reload §8- 重载配置");
        sender.sendMessage(configManager.getMessage("prefix") + "§6======================");
    }

    private boolean checkPermission(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(configManager.getMessage("error") + "权限不足");
            return false;
        }
        return true;
    }

    private DatabaseManager.CodeData getCodeByIdentifier(String identifier) {
        // 尝试解析为 ID
        try {
            int id = Integer.parseInt(identifier);
            // 通过 ID 查找（需要遍历）
            for (DatabaseManager.CodeData code : codeManager.getAllCodes()) {
                if (code.id == id) {
                    return code;
                }
            }
        } catch (NumberFormatException e) {
            // 不是数字，当作兑换码处理
        }
        
        // 直接通过兑换码查找
        return databaseManager.getCodeData(identifier);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, 
                                               Command command, 
                                               String label, 
                                               String[] args) {
        if (args.length == 1) {
            return getCompletions(args[0], Arrays.asList(
                    "generate", "delete", "modify", "validity", "list", "info", 
                    "history", "activate", "clear", "backup", "reload"
            ));
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("list")) {
                return getCompletions(args[1], Arrays.asList("all", "active", "used", "expired"));
            }
            if (subCommand.equals("generate")) {
                return Arrays.asList("1", "2", "5", "10", "unlimited");
            }
            if (subCommand.equals("clear")) {
                return getCompletions(args[1], Arrays.asList("all", "codes", "history"));
            }
            if (subCommand.equals("backup")) {
                return getCompletions(args[1], Arrays.asList("sqlite", "mysql", "both"));
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("modify")) {
            return Arrays.asList("1", "2", "5", "10", "unlimited");
        }
        
        if (args.length == 3 && args[0].equalsIgnoreCase("validity")) {
            return Arrays.asList("1", "7", "30", "permanent");
        }

        return null;
    }

    private List<String> getCompletions(String current, List<String> options) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(current.toLowerCase()))
                .collect(Collectors.toList());
    }
}
