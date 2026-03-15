package top.mcocet.bigExchange.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import top.mcocet.bigExchange.listener.FormListener;
import top.mcocet.bigExchange.manager.CodeManager;
import top.mcocet.bigExchange.manager.ConfigManager;
import top.mcocet.bigExchange.manager.LogManager;

public class RedeemCommand implements CommandExecutor {
    private final CodeManager codeManager;
    private final ConfigManager configManager;
    private final FormListener formListener;
    private final LogManager logManager;

    public RedeemCommand(CodeManager codeManager, ConfigManager configManager, FormListener formListener) {
        this.codeManager = codeManager;
        this.configManager = configManager;
        this.formListener = formListener;
        this.logManager = new LogManager(codeManager.getPlugin(), configManager);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, 
                            String label, String[] args) {
            
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c 只有玩家可以使用此命令！");
            return true;
        }
    
        logManager.fine("玩家 " + player.getName() + " 执行了 /redeem 命令，参数：" + (args.length > 0 ? String.join(" ", args) : "无"));
    
        if (args.length == 0) {
            // 打开兑换界面（表单或聊天输入）
            logManager.fine("玩家未提供兑换码，准备打开表单界面");
            if (formListener != null) {
                formListener.sendRedeemForm(player);
            } else {
                logManager.warning("FormListener 为 null，使用聊天栏代替");
                player.sendMessage(configManager.getMessage("prefix") + 
                        "§6=== 兑换码兑换 ===");
                player.sendMessage("§7 请输入兑换码：");
                player.sendMessage("§7 格式：§bXXXXXX-XXXXXXXXXX");
            }
            return true;
        }
    
        // 合并所有参数作为兑换码（防止有空格）
        StringBuilder codeBuilder = new StringBuilder();
        for (String arg : args) {
            if (codeBuilder.length() > 0) {
                codeBuilder.append(" ");
            }
            codeBuilder.append(arg);
        }
        String code = codeBuilder.toString().trim();
        logManager.fine("玩家输入的兑换码：" + code);
    
        // 处理兑换码
        logManager.fine("开始验证并使用兑换码...");
        CodeManager.UseResult useResult = codeManager.useCode(code, player.getUniqueId(), player.getName());
        boolean success = useResult.success;
        logManager.fine("兑换码使用结果：" + (success ? "成功" : "失败"));
        
        if (success) {
            // 延迟 1 tick 发送消息，确保奖励命令先执行
            org.bukkit.Bukkit.getScheduler().runTaskLater(codeManager.getPlugin(), () -> {
                player.sendMessage(configManager.getMessage("code-redeemed"));
                
                // 显示剩余次数（如果不是无限次）
                if (useResult.codeData != null && !useResult.codeData.isUnlimited()) {
                    int remaining = useResult.codeData.getRemainingUses();
                    if (remaining > 0) {
                        java.util.Map<String, String> placeholders = new java.util.HashMap<>();
                        placeholders.put("%remaining%", String.valueOf(remaining));
                        placeholders.put("%total%", String.valueOf(useResult.codeData.uses));
                        player.sendMessage(configManager.getMessage("code-remaining-uses", placeholders));
                    }
                }
                
                // 显示有效期信息
                if (useResult.codeData != null && useResult.codeData.hasValidity()) {
                    java.util.Map<String, String> placeholders = new java.util.HashMap<>();
                    placeholders.put("%remaining_time%", useResult.codeData.getFormattedRemainingTime());
                    player.sendMessage(configManager.getMessage("code-validity-remaining", placeholders));
                }
            }, 1L);
        } else {
            CodeManager.VerifyResult result = codeManager.verifyCode(code);
            if (!result.isValid) {
                switch (result.status) {
                    case NOT_FOUND, INVALID_FORMAT -> 
                        player.sendMessage(configManager.getMessage("code-invalid"));
                    case INACTIVE -> 
                        player.sendMessage(configManager.getMessage("code-expired"));
                    case EXPIRED -> 
                        player.sendMessage(configManager.getMessage("code-expired"));
                    case NO_USES_LEFT -> {
                        // 显示详细的使用限制信息
                        if (result.codeData != null) {
                            java.util.Map<String, String> placeholders = new java.util.HashMap<>();
                            placeholders.put("%used%", String.valueOf(result.codeData.usedCount));
                            placeholders.put("%total%", result.codeData.isUnlimited() ? "∞" : String.valueOf(result.codeData.uses));
                            player.sendMessage(configManager.getMessage("code-limit-reached", placeholders));
                        } else {
                            player.sendMessage(configManager.getMessage("code-no-uses"));
                        }
                    }
                    default -> 
                        player.sendMessage(configManager.getMessage("error"));
                }
            }
        }

        return true;
    }
}
