package top.mcocet.bigExchange.manager;

import org.bukkit.Bukkit;
import top.mcocet.bigExchange.BigExchange;

import java.util.List;

/**
 * 兑换码过期检查任务
 */
public class CodeExpirationTask {
    private final BigExchange plugin;
    private final DatabaseManager databaseManager;
    private final LogManager logManager;
    private int taskId;

    public CodeExpirationTask(BigExchange plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.logManager = new LogManager(plugin, plugin.getConfigManager());
    }

    /**
     * 启动定时任务
     * @param intervalMinutes 检查间隔（分钟）
     */
    public void start(int intervalMinutes) {
        if (taskId != -1) {
            stop();
        }

        long intervalTicks = intervalMinutes * 60L * 20L; // 转换为 tick
        
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            checkAndDeactivateExpiredCodes();
        }, intervalTicks, intervalTicks);

        logManager.info("已启动兑换码过期检查任务，检查间隔：" + intervalMinutes + "分钟");
    }

    /**
     * 停止定时任务
     */
    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
            logManager.info("已停止兑换码过期检查任务");
        }
    }

    /**
     * 检查并停用所有过期的兑换码
     */
    private void checkAndDeactivateExpiredCodes() {
        List<DatabaseManager.CodeData> codes = databaseManager.getAllCodes();
        int deactivatedCount = 0;

        for (DatabaseManager.CodeData code : codes) {
            if (code.isActive && code.isExpired()) {
                // 兑换码已过期，停用它
                if (databaseManager.deactivateCode(code.id)) {
                    deactivatedCount++;
                    logManager.info("兑换码 " + code.code + " 已过期，已自动停用");
                }
            }
        }

        if (deactivatedCount > 0) {
            logManager.info("本次检查共停用 " + deactivatedCount + " 个过期兑换码");
        }
    }

    /**
     * 立即执行一次检查
     */
    public void runOnce() {
        checkAndDeactivateExpiredCodes();
    }
}
