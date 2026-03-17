package top.mcocet.bigExchange.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import top.mcocet.bigExchange.BigExchange;
import top.mcocet.bigExchange.manager.ConfigManager;
import top.mcocet.bigExchange.manager.LogManager;
import top.mcocet.bigExchange.manager.craftconomy3.Craftconomy3MySQLHandler;

import java.util.*;

/**
 * Craftconomy3 数据查询命令处理器
 */
public class Craftconomy3QueryCommand implements CommandExecutor, TabCompleter {
    private final BigExchange plugin;
    private final ConfigManager configManager;
    private final LogManager logManager;
    
    public Craftconomy3QueryCommand(BigExchange plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.logManager = new LogManager(plugin, configManager);
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, 
                            String label, String[] args) {
        
        // 检查是否启用 Craftconomy3
        if (!configManager.isCraftconomy3Enabled()) {
            sender.sendMessage("§8[§6BigExchange§8] §c Craftconomy3 功能未启用！");
            return true;
        }
        
        // 获取数据库处理器
        Craftconomy3MySQLHandler handler = plugin.getCraftconomy3Handler();
        if (handler == null || !handler.isConnected()) {
            sender.sendMessage("§8[§6BigExchange§8] §c Craftconomy3 数据库未连接！");
            logManager.severe("Craftconomy3 数据库处理器未连接，无法查询");
            return true;
        }
        
        // 检查权限
        if (!sender.hasPermission("bigexchange.craftconomy3.query")) {
            sender.sendMessage("§8[§6BigExchange§8] §c 权限不足！");
            return true;
        }
        
        // 处理命令参数
        if (args.length == 0) {
            // 查询所有数据（所有世界组、所有玩家、所有货币）
            queryAllData(sender, handler);
            return true;
        }
        
        String playerName = args[0];
        
        // 查询指定玩家
        if (args.length == 1) {
            // 查询所有在线玩家
            if (playerName.equalsIgnoreCase("*all*")) {
                queryAllOnlinePlayers(sender, handler);
                return true;
            }
            // 查询指定玩家（使用配置的世界组和货币）
            queryPlayerBalance(sender, handler, playerName, 
                             configManager.getCraftconomy3WorldGroup(), 
                             configManager.getCraftconomy3CurrencyName());
            return true;
        }
        
        // 查询指定玩家的指定世界组和货币
        if (args.length >= 3) {
            String worldGroup = args[1];
            String currency = args[2];
            queryPlayerBalance(sender, handler, playerName, worldGroup, currency);
            return true;
        }
        
        sender.sendMessage("§8[§6BigExchange§8] §7 用法：/c3query [玩家名称] [世界组] [货币]");
        sender.sendMessage("§8[§6BigExchange§8] §7 用法：/c3query *all* - 查询所有在线玩家");
        sender.sendMessage("§8[§6BigExchange§8] §7 不填参数 - 查询所有配置数据");
        return true;
    }
    
    /**
     * 查询所有配置数据（所有世界组、所有玩家、所有货币）
     */
    private void queryAllData(CommandSender sender, Craftconomy3MySQLHandler handler) {
        logManager.fine("查询所有 Craftconomy3 数据");
        
        sender.sendMessage("§8[§6BigExchange§8] §7===== Craftconomy3 全量数据查询 =====");
        
        // 查询所有账户数据
        handler.queryAllAccounts(sender, logManager);
        
        sender.sendMessage("§8[§6BigExchange§8] §7======================================");
    }
    
    /**
     * 查询指定玩家的余额
     */
    private void queryPlayerBalance(CommandSender sender, Craftconomy3MySQLHandler handler, 
                                   String playerName, String worldGroup, String currency) {
        logManager.fine("查询玩家 " + playerName + " 的 Craftconomy3 数据（世界组：" + worldGroup + ", 货币：" + currency + ")");
        
        double balance = handler.getPlayerBalance(playerName, worldGroup, currency);
        boolean hasAccount = balance > 0 || handler.hasAccount(playerName, worldGroup, currency);
        
        if (hasAccount) {
            sender.sendMessage("§8[§6BigExchange§8] §7===== Craftconomy3 数据查询 =====");
            sender.sendMessage("§8[§6BigExchange§8] §7 玩家：§b" + playerName);
            sender.sendMessage("§8[§6BigExchange§8] §7 余额：§a$" + balance);
            sender.sendMessage("§8[§6BigExchange§8] §7 世界组：§e" + worldGroup);
            sender.sendMessage("§8[§6BigExchange§8] §7 货币：§e" + currency);
            sender.sendMessage("§8[§6BigExchange§8] §7================================");
            logManager.fine("玩家 " + playerName + " 余额查询成功：" + balance);
        } else {
            sender.sendMessage("§8[§6BigExchange§8] §c 未找到玩家 " + playerName + " 的账户数据！");
            sender.sendMessage("§8[§6BigExchange§8] §7 可能原因：");
            sender.sendMessage("§8[§6BigExchange§8] §7 1. 玩家名称错误");
            sender.sendMessage("§8[§6BigExchange§8] §7 2. 玩家在该世界组中没有账户");
            sender.sendMessage("§8[§6BigExchange§8] §7 3. 货币名称不匹配");
            logManager.warning("未找到玩家 " + playerName + " 的账户数据（世界组：" + worldGroup + ", 货币：" + currency + ")");
        }
    }
    
    /**
     * 查询所有在线玩家的余额
     */
    private void queryAllOnlinePlayers(CommandSender sender, Craftconomy3MySQLHandler handler) {
        if (!sender.hasPermission("bigexchange.craftconomy3.query.all")) {
            sender.sendMessage("§8[§6BigExchange§8] §c 权限不足！");
            return;
        }
        
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        
        sender.sendMessage("§8[§6BigExchange§8] §7===== Craftconomy3 数据查询 - 所有在线玩家 =====");
        sender.sendMessage("§8[§6BigExchange§8] §7 在线玩家数：§e" + players.size());
        sender.sendMessage("§8[§6BigExchange§8] §7 世界组：§e" + configManager.getCraftconomy3WorldGroup());
        sender.sendMessage("§8[§6BigExchange§8] §7 货币：§e" + configManager.getCraftconomy3CurrencyName());
        sender.sendMessage("§8[§6BigExchange§8] §7===========================================");
        
        int count = 0;
        for (Player player : players) {
            double balance = handler.getPlayerBalance(player.getName(), 
                                                     configManager.getCraftconomy3WorldGroup(), 
                                                     configManager.getCraftconomy3CurrencyName());
            sender.sendMessage("§8[§6BigExchange§8] §b" + player.getName() + " §7- 余额：§a$" + balance);
            count++;
        }
        
        sender.sendMessage("§8[§6BigExchange§8] §7===========================================");
        sender.sendMessage("§8[§6BigExchange§8] §7 共查询 §e" + count + " §7 名玩家");
        logManager.fine("批量查询所有在线玩家余额，共 " + count + " 名");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, 
                                     String alias, String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }
        
        List<String> completions = new ArrayList<>();
        String partial = args[0].toLowerCase();
        
        // 添加 *all* 选项（如果有权限）
        if (sender.hasPermission("bigexchange.craftconomy3.query.all")) {
            completions.add("*all*");
        }
        
        // 添加在线玩家名称
        for (Player player : Bukkit.getOnlinePlayers()) {
            String name = player.getName().toLowerCase();
            if (StringUtil.startsWithIgnoreCase(name, partial)) {
                completions.add(player.getName());
            }
        }
        
        return completions;
    }
}
