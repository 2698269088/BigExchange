package top.mcocet.bigExchange.manager;

import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * 日志管理器 - 统一处理插件日志输出
 */
public class LogManager {
    
    private final Plugin plugin;
    private final ConfigManager configManager;
    
    public LogManager(Plugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }
    
    /**
     * 记录 INFO 级别日志
     */
    public void info(String message) {
        if (configManager.shouldLog("INFO")) {
            plugin.getLogger().info(message);
        }
    }
    
    /**
     * 记录 WARNING 级别日志
     */
    public void warning(String message) {
        if (configManager.shouldLog("WARNING")) {
            plugin.getLogger().warning(message);
        }
    }
    
    /**
     * 记录 SEVERE 级别日志
     */
    public void severe(String message) {
        if (configManager.shouldLog("SEVERE")) {
            plugin.getLogger().severe(message);
        }
    }
    
    /**
     * 记录 FINE 级别日志（调试）
     */
    public void fine(String message) {
        if (configManager.shouldLog("FINE")) {
            plugin.getLogger().fine(message);
        }
    }
    
    /**
     * 记录 FINER 级别日志（详细调试）
     */
    public void finer(String message) {
        if (configManager.shouldLog("FINER")) {
            plugin.getLogger().finer(message);
        }
    }
    
    /**
     * 记录 FINEST 级别日志（最详细调试）
     */
    public void finest(String message) {
        if (configManager.shouldLog("FINEST")) {
            plugin.getLogger().finest(message);
        }
    }
    
    /**
     * 记录异常日志
     */
    public void exception(String message, Throwable throwable) {
        if (configManager.shouldLog("SEVERE")) {
            plugin.getLogger().severe(message);
            // 打印异常堆栈
            throwable.printStackTrace();
        }
    }
}
