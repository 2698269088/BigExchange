package top.mcocet.bigExchange.manager;

import top.mcocet.bigExchange.BigExchange;

import java.util.List;
import java.util.UUID;

/**
 * 数据库管理器 - 负责调度 SQLite 和 MySQL 处理器
 */
public class DatabaseManager {
    private final BigExchange plugin;
    private final ConfigManager configManager;
    private final LogManager logManager;
    
    // 数据库处理器
    private SQLiteDatabaseHandler sqliteHandler;
    private MySQLDatabaseHandler mysqlHandler;
    
    // 当前数据库类型
    private DatabaseType activeDatabaseType;
    
    // 是否启用双备份
    private boolean dualBackupEnabled;

    public DatabaseManager(BigExchange plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.logManager = new LogManager(plugin, configManager);
        this.dualBackupEnabled = configManager.isDualBackupEnabled();
        
        initializeDatabases();
    }

    /**
     * 初始化数据库
     */
    private void initializeDatabases() {
        String dbType = configManager.getDatabaseType().toLowerCase();
        
        if (dualBackupEnabled) {
            // 双备份模式：同时初始化 SQLite 和 MySQL
            logManager.info("启用双数据库备份模式...");
            try {
                sqliteHandler = new SQLiteDatabaseHandler(configManager.getDatabasePath());
                sqliteHandler.initialize();
                logManager.info("SQLite 数据库连接成功！");
                
                mysqlHandler = new MySQLDatabaseHandler(
                    configManager.getMysqlHost(),
                    configManager.getMysqlPort(),
                    configManager.getMysqlDatabase(),
                    configManager.getMysqlUsername(),
                    configManager.getMysqlPassword(),
                    configManager.getMysqlTablePrefix(),
                    configManager.getMysqlPoolMinSize(),
                    configManager.getMysqlPoolMaxSize(),
                    configManager.getMysqlPoolMaxLifetime()
                );
                mysqlHandler.initialize();
                logManager.info("MySQL 数据库连接成功！");
                
                activeDatabaseType = DatabaseType.BOTH;
            } catch (Exception e) {
                logManager.severe("初始化数据库失败：" + e.getMessage());
            }
        } else {
            // 单数据库模式
            if ("mysql".equalsIgnoreCase(dbType)) {
                try {
                    mysqlHandler = new MySQLDatabaseHandler(
                        configManager.getMysqlHost(),
                        configManager.getMysqlPort(),
                        configManager.getMysqlDatabase(),
                        configManager.getMysqlUsername(),
                        configManager.getMysqlPassword(),
                        configManager.getMysqlTablePrefix(),
                        configManager.getMysqlPoolMinSize(),
                        configManager.getMysqlPoolMaxSize(),
                        configManager.getMysqlPoolMaxLifetime()
                    );
                    mysqlHandler.initialize();
                    activeDatabaseType = DatabaseType.MYSQL;
                    logManager.info("MySQL 数据库连接成功！");
                } catch (Exception e) {
                    logManager.severe("初始化 MySQL 数据库失败：" + e.getMessage());
                }
            } else {
                try {
                    sqliteHandler = new SQLiteDatabaseHandler(configManager.getDatabasePath());
                    sqliteHandler.initialize();
                    activeDatabaseType = DatabaseType.SQLITE;
                    logManager.info("SQLite 数据库连接成功！");
                } catch (Exception e) {
                    logManager.severe("初始化 SQLite 数据库失败：" + e.getMessage());
                }
            }
        }
        
        logManager.info("当前数据库类型：" + activeDatabaseType);
    }

    /**
     * 保存兑换码
     */
    public void saveCode(String code, String codeHash, int uses, String createdBy, String rewardCommands,
                        java.sql.Timestamp expirationTime, int validityDays) {
        if (activeDatabaseType == DatabaseType.SQLITE || activeDatabaseType == DatabaseType.BOTH) {
            sqliteHandler.saveCode(code, codeHash, uses, createdBy, rewardCommands, expirationTime, validityDays);
        }
        if (activeDatabaseType == DatabaseType.MYSQL || activeDatabaseType == DatabaseType.BOTH) {
            mysqlHandler.saveCode(code, codeHash, uses, createdBy, rewardCommands, expirationTime, validityDays);
        }
    }

    /**
     * 保存兑换码（兼容旧版本）
     */
    public void saveCode(String code, String codeHash, int uses, String createdBy, String rewardCommands) {
        saveCode(code, codeHash, uses, createdBy, rewardCommands, null, -1);
    }

    /**
     * 检查兑换码是否存在
     */
    public boolean codeExists(String code) {
        if (activeDatabaseType == DatabaseType.SQLITE || activeDatabaseType == DatabaseType.BOTH) {
            if (sqliteHandler.codeExists(code)) return true;
        }
        if (activeDatabaseType == DatabaseType.MYSQL || activeDatabaseType == DatabaseType.BOTH) {
            if (mysqlHandler.codeExists(code)) return true;
        }
        return false;
    }

    /**
     * 获取兑换码数据
     */
    public CodeData getCodeData(String code) {
        // 优先从主数据库获取
        if (activeDatabaseType == DatabaseType.SQLITE || activeDatabaseType == DatabaseType.BOTH) {
            CodeData data = sqliteHandler.getCodeData(code);
            if (data != null) return data;
        }
        if (activeDatabaseType == DatabaseType.MYSQL || activeDatabaseType == DatabaseType.BOTH) {
            return mysqlHandler.getCodeData(code);
        }
        return null;
    }

    /**
     * 更新兑换码使用次数
     */
    public boolean updateCodeUses(int codeId, int newUses) {
        boolean success = true;
        if (activeDatabaseType == DatabaseType.SQLITE || activeDatabaseType == DatabaseType.BOTH) {
            success &= sqliteHandler.updateCodeUses(codeId, newUses);
        }
        if (activeDatabaseType == DatabaseType.MYSQL || activeDatabaseType == DatabaseType.BOTH) {
            success &= mysqlHandler.updateCodeUses(codeId, newUses);
        }
        return success;
    }

    /**
     * 更新兑换码有效期
     */
    public boolean updateCodeValidity(int codeId, int validityDays) {
        boolean success = true;
        if (activeDatabaseType == DatabaseType.SQLITE || activeDatabaseType == DatabaseType.BOTH) {
            success &= sqliteHandler.updateCodeValidity(codeId, validityDays);
        }
        if (activeDatabaseType == DatabaseType.MYSQL || activeDatabaseType == DatabaseType.BOTH) {
            success &= mysqlHandler.updateCodeValidity(codeId, validityDays);
        }
        return success;
    }

    /**
     * 增加已使用次数
     */
    public boolean incrementUsedCount(int codeId) {
        boolean success = true;
        if (activeDatabaseType == DatabaseType.SQLITE || activeDatabaseType == DatabaseType.BOTH) {
            success &= sqliteHandler.incrementUsedCount(codeId);
        }
        if (activeDatabaseType == DatabaseType.MYSQL || activeDatabaseType == DatabaseType.BOTH) {
            success &= mysqlHandler.incrementUsedCount(codeId);
        }
        return success;
    }

    /**
     * 停用兑换码
     */
    public boolean deactivateCode(int codeId) {
        boolean success = true;
        if (activeDatabaseType == DatabaseType.SQLITE || activeDatabaseType == DatabaseType.BOTH) {
            success &= sqliteHandler.deactivateCode(codeId);
        }
        if (activeDatabaseType == DatabaseType.MYSQL || activeDatabaseType == DatabaseType.BOTH) {
            success &= mysqlHandler.deactivateCode(codeId);
        }
        return success;
    }

    /**
     * 激活兑换码
     */
    public boolean activateCode(int codeId) {
        boolean success = true;
        if (activeDatabaseType == DatabaseType.SQLITE || activeDatabaseType == DatabaseType.BOTH) {
            success &= sqliteHandler.activateCode(codeId);
        }
        if (activeDatabaseType == DatabaseType.MYSQL || activeDatabaseType == DatabaseType.BOTH) {
            success &= mysqlHandler.activateCode(codeId);
        }
        return success;
    }

    /**
     * 记录使用历史
     */
    public void recordUsage(int codeId, UUID playerUuid, String playerName) {
        if (activeDatabaseType == DatabaseType.SQLITE || activeDatabaseType == DatabaseType.BOTH) {
            sqliteHandler.recordUsage(codeId, playerUuid, playerName);
        }
        if (activeDatabaseType == DatabaseType.MYSQL || activeDatabaseType == DatabaseType.BOTH) {
            mysqlHandler.recordUsage(codeId, playerUuid, playerName);
        }
    }

    /**
     * 获取所有兑换码
     */
    public List<CodeData> getAllCodes() {
        if (activeDatabaseType == DatabaseType.MYSQL) {
            return mysqlHandler.getAllCodes();
        } else {
            return sqliteHandler.getAllCodes();
        }
    }

    /**
     * 删除兑换码
     */
    public boolean deleteCode(int codeId) {
        boolean success = true;
        if (activeDatabaseType == DatabaseType.SQLITE || activeDatabaseType == DatabaseType.BOTH) {
            success &= sqliteHandler.deleteCode(codeId);
        }
        if (activeDatabaseType == DatabaseType.MYSQL || activeDatabaseType == DatabaseType.BOTH) {
            success &= mysqlHandler.deleteCode(codeId);
        }
        return success;
    }

    /**
     * 获取使用历史
     */
    public List<CodeUsage> getUsageHistory(int codeId) {
        if (activeDatabaseType == DatabaseType.MYSQL) {
            return mysqlHandler.getUsageHistory(codeId);
        } else {
            return sqliteHandler.getUsageHistory(codeId);
        }
    }

    /**
     * 执行数据库备份
     */
    public void performBackup() {
        if (!dualBackupEnabled) {
            logManager.warning("未启用双备份模式，无法执行备份");
            return;
        }
        
        logManager.info("正在执行数据库备份...");
        
        // SQLite -> MySQL
        if (sqliteHandler != null && mysqlHandler != null) {
            sqliteHandler.backupTo(mysqlHandler);
            logManager.info("SQLite -> MySQL 备份完成");
        }
        
        // MySQL -> SQLite
        if (mysqlHandler != null && sqliteHandler != null) {
            mysqlHandler.backupTo(sqliteHandler);
            logManager.info("MySQL -> SQLite 备份完成");
        }
        
        logManager.info("数据库备份完成");
    }

    /**
     * 关闭所有数据库连接
     */
    public void close() {
        if (sqliteHandler != null) {
            sqliteHandler.close();
        }
        if (mysqlHandler != null) {
            mysqlHandler.close();
        }
        logManager.info("数据库连接已关闭");
    }

    /**
     * 检查数据库是否连接
     */
    public boolean isConnected() {
        if (activeDatabaseType == DatabaseType.SQLITE) {
            return sqliteHandler != null && sqliteHandler.isConnected();
        } else if (activeDatabaseType == DatabaseType.MYSQL) {
            return mysqlHandler != null && mysqlHandler.isConnected();
        } else {
            return (sqliteHandler != null && sqliteHandler.isConnected()) &&
                   (mysqlHandler != null && mysqlHandler.isConnected());
        }
    }

    /**
     * 获取当前数据库类型
     */
    public DatabaseType getActiveDatabaseType() {
        return activeDatabaseType;
    }

    // 数据类定义
    public static class CodeData {
        public final int id;
        public final String code;
        public final String codeHash;
        public final int uses;
        public final int usedCount;
        public final String createdBy;
        public final java.sql.Timestamp createdTime;
        public final boolean isActive;
        public final java.sql.Timestamp lastUsed;
        public final String rewardCommands; // 奖励命令（分号分隔）
        public final java.sql.Timestamp expirationTime; // 过期时间
        public final int validityDays; // 有效期天数（-1 表示永久）

        public CodeData(int id, String code, String codeHash, int uses, int usedCount,
                       String createdBy, java.sql.Timestamp createdTime, 
                       boolean isActive, java.sql.Timestamp lastUsed, String rewardCommands,
                       java.sql.Timestamp expirationTime, int validityDays) {
            this.id = id;
            this.code = code;
            this.codeHash = codeHash;
            this.uses = uses;
            this.usedCount = usedCount;
            this.createdBy = createdBy;
            this.createdTime = createdTime;
            this.isActive = isActive;
            this.lastUsed = lastUsed;
            this.rewardCommands = rewardCommands;
            this.expirationTime = expirationTime;
            this.validityDays = validityDays;
        }

        // 兼容旧版本构造函数
        public CodeData(int id, String code, String codeHash, int uses, int usedCount,
                       String createdBy, java.sql.Timestamp createdTime, 
                       boolean isActive, java.sql.Timestamp lastUsed, String rewardCommands) {
            this(id, code, codeHash, uses, usedCount, createdBy, createdTime, isActive, lastUsed, rewardCommands, null, -1);
        }

        public int getRemainingUses() {
            return uses == -1 ? -1 : uses - usedCount;
        }

        public boolean isUnlimited() {
            return uses == -1;
        }

        public boolean hasUsesLeft() {
            return uses == -1 || usedCount < uses;
        }

        public boolean isExpired() {
            if (expirationTime == null) return false;
            return new java.sql.Timestamp(System.currentTimeMillis()).after(expirationTime);
        }

        public boolean hasValidity() {
            return validityDays != -1;
        }

        public long getRemainingTimeMillis() {
            if (expirationTime == null) return -1;
            long remaining = expirationTime.getTime() - System.currentTimeMillis();
            return remaining > 0 ? remaining : 0;
        }

        public String getFormattedRemainingTime() {
            long remaining = getRemainingTimeMillis();
            if (remaining == -1) return "永久有效";
            if (remaining <= 0) return "已过期";
            
            long days = remaining / (1000 * 60 * 60 * 24);
            long hours = (remaining % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60);
            long minutes = (remaining % (1000 * 60 * 60)) / (1000 * 60);
            
            if (days > 0) {
                return String.format("%d天%d小时", days, hours);
            } else if (hours > 0) {
                return String.format("%d小时%d分钟", hours, minutes);
            } else {
                return String.format("%d分钟", minutes);
            }
        }
    }

    public static class CodeUsage {
        public final int id;
        public final int codeId;
        public final String playerUuid;
        public final String playerName;
        public final java.sql.Timestamp usedTime;

        public CodeUsage(int id, int codeId, String playerUuid, String playerName, 
                        java.sql.Timestamp usedTime) {
            this.id = id;
            this.codeId = codeId;
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.usedTime = usedTime;
        }
    }

    /**
     * 数据库类型枚举
     */
    public enum DatabaseType {
        SQLITE,
        MYSQL,
        BOTH
    }
}
