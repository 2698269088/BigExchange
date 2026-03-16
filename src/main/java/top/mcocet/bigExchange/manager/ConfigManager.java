package top.mcocet.bigExchange.manager;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;

public class ConfigManager {
    private final Plugin plugin;
    private FileConfiguration config;
    private File configFile;

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
        saveDefaultConfig();
    }

    public void saveDefaultConfig() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        
        reloadConfig();
    }

    public void reloadConfig() {
        config = plugin.getConfig();
        config.options().copyDefaults(true);
        
        // 默认配置
        config.addDefault("database.enabled", true);
        config.addDefault("database.path", "plugins/BigExchange/codes.db");
        config.addDefault("code.format.first-length", 6);
        config.addDefault("code.format.second-length", 10);
        config.addDefault("code.format.separator", "-");
        config.addDefault("code.security.encrypt", true);
        config.addDefault("code.security.algorithm", "SHA-256");
        config.addDefault("code.validity.default-days", 30); // 默认 30 天有效期
        config.addDefault("messages.prefix", "&8[&6BigExchange&8] ");
        config.addDefault("messages.success", "&a操作成功！");
        config.addDefault("messages.error", "&c操作失败！");
        config.addDefault("messages.code-generated", "&7已生成兑换码：&b%code% &7，可用次数：%uses%");
        config.addDefault("messages.code-redeemed", "&a兑换成功！获得奖励");
        config.addDefault("messages.code-invalid", "&c无效的兑换码");
        config.addDefault("messages.code-expired", "&c兑换码已失效");
        config.addDefault("messages.code-no-uses", "&c该兑换码已无可用次数");
        config.addDefault("messages.admin-force-activated", "&a 强制激活兑换码成功");
        config.addDefault("logging.level", "INFO");
        
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("无法保存配置文件：" + e.getMessage());
        }
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public String getMessage(String path) {
        return translateColorCodes(config.getString("messages.prefix", "&8[&6BigExchange&8] ") 
                + config.getString("messages." + path, ""));
    }

    public String translateColorCodes(String message) {
        return message.replace("&", "§");
    }

    public int getCodeFirstLength() {
        return config.getInt("code.format.first-length", 6);
    }

    public int getCodeSecondLength() {
        return config.getInt("code.format.second-length", 10);
    }

    public String getSeparator() {
        return config.getString("code.format.separator", "-");
    }

    public boolean isEncryptionEnabled() {
        return config.getBoolean("code.security.encrypt", true);
    }

    public String getDatabasePath() {
        return config.getString("database.path", "plugins/BigExchange/codes.db");
    }

    public boolean isDatabaseEnabled() {
        return config.getBoolean("database.enabled", true);
    }

    public String getDatabaseType() {
        return config.getString("database.type", "sqlite");
    }

    public String getMysqlHost() {
        return config.getString("database.mysql.host", "localhost");
    }

    public int getMysqlPort() {
        return config.getInt("database.mysql.port", 3306);
    }

    public String getMysqlDatabase() {
        return config.getString("database.mysql.database", "bigexchange");
    }

    public String getMysqlUsername() {
        return config.getString("database.mysql.username", "root");
    }

    public String getMysqlPassword() {
        return config.getString("database.mysql.password", "password");
    }

    public int getMysqlPoolMinSize() {
        return config.getInt("database.mysql.pool.min-size", 5);
    }

    public int getMysqlPoolMaxSize() {
        return config.getInt("database.mysql.pool.max-size", 20);
    }

    public long getMysqlPoolMaxLifetime() {
        return config.getLong("database.mysql.pool.max-lifetime", 1800000);
    }

    public String getMysqlTablePrefix() {
        return config.getString("database.mysql.table-prefix", "be_");
    }

    public boolean isDualBackupEnabled() {
        return config.getBoolean("database.backup.dual-backup", false);
    }

    public int getAutoBackupInterval() {
        return config.getInt("database.backup.auto-backup-interval", 60);
    }

    /**
     * 获取带占位符替换的消息
     */
    public String getMessage(String path, java.util.Map<String, String> placeholders) {
        String message = translateColorCodes(config.getString("messages.prefix", "&8[&6BigExchange&8] ")
                + config.getString("messages." + path, ""));
        if (placeholders != null) {
            for (java.util.Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace(entry.getKey(), entry.getValue());
            }
        }
        return message;
    }

    // 基岩版表单配置
    public boolean isFormEnabled() {
        return config.getBoolean("form.enabled", false);
    }

    /**
     * 检查是否启用 Java 版 AnvilGUI
     * @return true 表示启用
     */
    public boolean isAnvilGUIEnabled() {
        return config.getBoolean("form.anvil-gui-enabled", true);
    }

    public String getFormType() {
        return config.getString("form.type", "geyser");
    }

    /**
     * 检查是否使用 Geyser/Floodgate 表单
     * @return true 表示使用国际版表单
     */
    public boolean isGeyserForm() {
        return "geyser".equalsIgnoreCase(getFormType());
    }

    public String getFormRedeemTitle() {
        return translateColorCodes(config.getString("form.redeem-title", "&6 兑换码兑换"));
    }

    public String getFormRedeemContent() {
        return translateColorCodes(config.getString("form.redeem-content", "&e 请输入您的兑换码："));
    }

    public String getFormRedeemPlaceholder() {
        return config.getString("form.redeem-placeholder", "XXXXXX-XXXXXXXXXX");
    }

    public String getFormRedeemButtonConfirm() {
        return translateColorCodes(config.getString("form.redeem-button-confirm", "&a 确认兑换"));
    }

    public String getFormRedeemButtonCancel() {
        return translateColorCodes(config.getString("form.redeem-button-cancel", "&c 取消"));
    }

    public String getFormDetectionMode() {
        return config.getString("form.detection-mode", "auto");
    }

    public String getLogLevel() {
        return config.getString("logging.level", "INFO");
    }

    /**
     * 检查是否应该记录指定级别的日志
     * @param level 日志级别
     * @return 是否应该记录
     */
    public boolean shouldLog(String level) {
        String configuredLevel = getLogLevel().toUpperCase();
        
        // 定义日志级别顺序：SEVERE > WARNING > INFO > FINE > FINER > FINEST
        java.util.List<String> levels = java.util.Arrays.asList(
            "SEVERE", "WARNING", "INFO", "FINE", "FINER", "FINEST"
        );
        
        int configuredIndex = levels.indexOf(configuredLevel);
        int targetIndex = levels.indexOf(level.toUpperCase());
        
        if (configuredIndex == -1 || targetIndex == -1) {
            return true; // 如果级别无效，默认记录
        }
        
        // 级别值越小，优先级越高（越严重）
        return targetIndex <= configuredIndex;
    }

    /**
     * 获取默认有效期天数
     * @return 有效期天数（-1 表示永久）
     */
    public int getDefaultValidityDays() {
        return config.getInt("code.validity.default-days", 30);
    }

    /**
     * 获取自动过期检查间隔（分钟）
     * @return 检查间隔（0 表示禁用）
     */
    public int getAutoCheckInterval() {
        return config.getInt("code.validity.auto-check-interval", 60);
    }
}
