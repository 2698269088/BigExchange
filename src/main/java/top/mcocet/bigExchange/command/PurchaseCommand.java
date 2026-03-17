package top.mcocet.bigExchange.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import top.mcocet.bigExchange.BigExchange;
import top.mcocet.bigExchange.manager.CodeManager;
import top.mcocet.bigExchange.manager.ConfigManager;
import top.mcocet.bigExchange.manager.LogManager;
import top.mcocet.bigExchange.manager.craftconomy3.Craftconomy3MySQLHandler;

import java.util.Random;

/**
 * 购买兑换码命令处理器
 */
public class PurchaseCommand implements CommandExecutor {
    private final BigExchange plugin;
    private final CodeManager codeManager;
    private final ConfigManager configManager;
    private final LogManager logManager;
    
    public PurchaseCommand(BigExchange plugin, CodeManager codeManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.codeManager = codeManager;
        this.configManager = configManager;
        this.logManager = new LogManager(plugin, configManager);
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, 
                            String label, String[] args) {
            
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("prefix") + "§c 只有玩家可以使用此命令！");
            return true;
        }
            
        // 检查权限
        if (!player.hasPermission("bigexchange.purchase")) {
            sender.sendMessage(configManager.getMessage("prefix") + "§c 权限不足！");
            return true;
        }
            
        // 检查是否启用购买功能
        if (!configManager.isPurchaseEnabled()) {
            player.sendMessage(configManager.getMessage("prefix") + "§c 购买功能暂未启用");
            return true;
        }
            
        double price = configManager.getPurchasePrice();
        int uses = configManager.getPurchaseUses();
        int validityDays = configManager.getPurchaseValidityDays();
            
        // 直接执行购买，不需要确认
        boolean success = processPurchaseDirect(player, price, uses, validityDays);
            
        if (!success) {
            player.sendMessage(configManager.getMessage("prefix") + "§c 购买失败，请查看控制台日志");
        }
            
        return true;
    }
    
    /**
     * 处理购买请求（直接调用版本）
     */
    public boolean processPurchaseDirect(Player player, double price, int uses, int validityDays) {
        // 如果启用了 Craftconomy3 扣款，从 Craftconomy3 扣除
        if (configManager.isCraftconomy3Enabled()) {
            boolean paymentSuccess = processCraftconomy3Payment(player, price);
            
            if (!paymentSuccess) {
                player.sendMessage(configManager.getMessage("prefix") + "§c 余额不足，无法购买（需要 $" + price + "）");
                logManager.warning("玩家 " + player.getName() + " 购买失败：余额不足（需要 " + price + "）");
                return false;
            }
        }
        // TODO: 如果未启用 Craftconomy3，可以集成其他经济插件（如 Vault）
        // 目前暂时免费赠送
        
        // 生成奖励命令（从配置中随机选择）
        String rewardCommand = getRandomRewardCommand(player.getName());
        
        // 生成兑换码
        String createdBy = "PURCHASE:" + player.getName();
        String code = codeManager.generateCode(uses, createdBy, rewardCommand, validityDays);
        
        // 发送成功消息
        player.sendMessage(configManager.getMessage("prefix") + "§a 购买成功！");
        player.sendMessage(configManager.getMessage("code-generated")
            .replace("%code%", code)
            .replace("%uses%", uses == -1 ? "无限" : String.valueOf(uses))
            .replace("%validity%", validityDays == -1 ? "永久有效" : validityDays + "天"));
        
        logManager.info("玩家 " + player.getName() + " 购买了兑换码：" + code);
        
        return true;
    }
    
    /**
     * 处理 Craftconomy3 扣款
     */
    private boolean processCraftconomy3Payment(Player player, double price) {
        // 使用插件实例中的全局处理器（不需要每次都创建和关闭）
        Craftconomy3MySQLHandler handler = plugin.getCraftconomy3Handler();
        
        if (handler == null || !handler.isConnected()) {
            logManager.severe("Craftconomy3 数据库连接不可用");
            return false;
        }
        
        // 检查余额并扣款
        if (!handler.hasEnough(player.getName(), price)) {
            logManager.fine("玩家 " + player.getName() + " 余额不足（需要 " + price + "）");
            return false;
        }
        
        return handler.withdrawPlayer(player.getName(), price);
    }
    
    /**
     * 从配置中随机选择一条奖励命令
     */
    private String getRandomRewardCommand(String playerName) {
        var commands = configManager.getPurchaseRewardCommands();
        if (commands.isEmpty()) {
            return "";
        }
        
        Random random = new Random();
        String command = commands.get(random.nextInt(commands.size()));
        
        // 替换占位符 %player%
        return command.replace("%player%", playerName);
    }
}
