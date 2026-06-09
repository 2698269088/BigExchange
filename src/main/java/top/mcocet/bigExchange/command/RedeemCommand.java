package top.mcocet.bigExchange.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import top.mcocet.bigExchange.listener.FormListener;
import top.mcocet.bigExchange.manager.CodeManager;
import top.mcocet.bigExchange.manager.ConfigManager;
import top.mcocet.bigExchange.manager.LogManager;
import top.mcocet.bigExchange.util.FoliaScheduler;

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
    
        // 将参数按空格分割为多个兑换码（支持一次输入多个）
        java.util.List<String> codes = new java.util.ArrayList<>();
        for (String arg : args) {
            // 按空格分割每个参数（处理玩家可能用空格分隔的情况）
            String[] parts = arg.split("\\s+");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    codes.add(trimmed);
                }
            }
        }
        
        logManager.fine("玩家输入的兑换码数量：" + codes.size());
        
        if (codes.isEmpty()) {
            player.sendMessage(configManager.getMessage("prefix") + "§c请输入兑换码！");
            return true;
        }
        
        // 限制最多处理20个兑换码
        if (codes.size() > 20) {
            player.sendMessage(configManager.getMessage("prefix") + "§c一次最多兑换20个兑换码！");
            codes = codes.subList(0, 20);
        }
        
        // 处理多个兑换码
        int successCount = 0;
        int failCount = 0;
        java.util.List<String> failedCodes = new java.util.ArrayList<>();
        
        for (String code : codes) {
            logManager.fine("处理兑换码：" + code);
            CodeManager.UseResult useResult = codeManager.useCode(code, player.getUniqueId(), player.getName());
            
            if (useResult.success) {
                successCount++;
            } else {
                failCount++;
                failedCodes.add(code);
            }
        }
        
        // 将变量设置为有效 final，以便在 lambda 中使用
        final int finalSuccessCount = successCount;
        final int finalFailCount = failCount;
        final java.util.List<String> finalFailedCodes = failedCodes;
        final Player finalPlayer = player;
        
        // 延迟发送结果消息（Folia 兼容）
        FoliaScheduler.runSyncLater(codeManager.getPlugin(), () -> {
            if (finalSuccessCount > 0) {
                finalPlayer.sendMessage(configManager.getMessage("prefix") + "§a成功兑换 " + finalSuccessCount + " 个兑换码！");
            }
            if (finalFailCount > 0) {
                finalPlayer.sendMessage(configManager.getMessage("prefix") + "§c失败 " + finalFailCount + " 个兑换码：" + String.join(", ", finalFailedCodes));
            }
        }, 1L);

        return true;
    }
}