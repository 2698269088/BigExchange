package top.mcocet.bigExchange.manager;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * MySQL 数据库处理器
 */
public class MySQLDatabaseHandler {
    private HikariDataSource dataSource;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final String tablePrefix;
    private final int poolMinSize;
    private final int poolMaxSize;
    private final long poolMaxLifetime;

    public MySQLDatabaseHandler(String host, int port, String database, String username, 
                               String password, String tablePrefix, int poolMinSize, 
                               int poolMaxSize, long poolMaxLifetime) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.tablePrefix = tablePrefix;
        this.poolMinSize = poolMinSize;
        this.poolMaxSize = poolMaxSize;
        this.poolMaxLifetime = poolMaxLifetime;
    }

    /**
     * 初始化数据库连接池
     */
    public void initialize() throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=false&characterEncoding=utf-8", 
                host, port, database));
        config.setUsername(username);
        config.setPassword(password);
        
        // 连接池配置
        config.setMinimumIdle(poolMinSize);
        config.setMaximumPoolSize(poolMaxSize);
        config.setMaxLifetime(poolMaxLifetime);
        
        // 其他优化配置
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        
        dataSource = new HikariDataSource(config);
        
        // 测试连接并创建表
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            createTables();
        }
    }

    /**
     * 创建数据表
     */
    private void createTables() throws SQLException {
        String codeTableName = getTableName("exchange_codes");
        String usageTableName = getTableName("code_usage");
        
        String sql = String.format("""
                CREATE TABLE IF NOT EXISTS %s (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    code VARCHAR(32) UNIQUE NOT NULL,
                    code_hash VARCHAR(64) NOT NULL,
                    uses INT DEFAULT -1,
                    used_count INT DEFAULT 0,
                    created_by VARCHAR(50),
                    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    is_active BOOLEAN DEFAULT TRUE,
                    last_used TIMESTAMP,
                    reward_commands TEXT,
                    expiration_time TIMESTAMP,
                    validity_days INT DEFAULT -1,
                    metadata TEXT
                )""", codeTableName);
        
        try (Statement stmt = dataSource.getConnection().createStatement()) {
            stmt.execute(sql);
        }
        
        // 检查并添加 reward_commands 字段（兼容旧版本）
        addColumnIfNotExists(codeTableName, "reward_commands", "TEXT");
        
        // 检查并添加 expiration_time 字段
        addColumnIfNotExists(codeTableName, "expiration_time", "TIMESTAMP");
        
        // 检查并添加 validity_days 字段
        addColumnIfNotExists(codeTableName, "validity_days", "INT");
        
        // 创建使用记录表
        String usageSql = String.format("""
                CREATE TABLE IF NOT EXISTS %s (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    code_id INT NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    player_name VARCHAR(50) NOT NULL,
                    used_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_code_id (code_id),
                    FOREIGN KEY (code_id) REFERENCES %s(id) ON DELETE CASCADE
                )""", usageTableName, codeTableName);
        
        try (Statement stmt = dataSource.getConnection().createStatement()) {
            stmt.execute(usageSql);
        }
    }

    /**
     * 检查并添加字段（如果不存在）
     */
    private void addColumnIfNotExists(String tableName, String columnName, String columnType) throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(checkSql)) {
            pstmt.setString(1, tableName);
            pstmt.setString(2, columnName);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next() && rs.getInt(1) > 0) {
                return; // 字段已存在
            }
        }
        
        // 字段不存在，添加字段
        String alterSql = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnType;
        try (Statement stmt = dataSource.getConnection().createStatement()) {
            stmt.execute(alterSql);
        }
    }

    /**
     * 保存兑换码
     */
    public void saveCode(String code, String codeHash, int uses, String createdBy, String rewardCommands,
                        java.sql.Timestamp expirationTime, int validityDays) {
        String sql = "INSERT INTO " + getTableName("exchange_codes") + 
                     " (code, code_hash, uses, created_by, reward_commands, expiration_time, validity_days) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, code);
            pstmt.setString(2, codeHash);
            pstmt.setInt(3, uses);
            pstmt.setString(4, createdBy);
            pstmt.setString(5, rewardCommands != null ? rewardCommands : "");
            pstmt.setTimestamp(6, expirationTime);
            pstmt.setInt(7, validityDays);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
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
        String sql = "SELECT COUNT(*) FROM " + getTableName("exchange_codes") + 
                     " WHERE code = ? OR code_hash = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, code);
            pstmt.setString(2, hashCode(code));
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * 获取兑换码数据
     */
    public DatabaseManager.CodeData getCodeData(String code) {
        String sql = "SELECT * FROM " + getTableName("exchange_codes") + 
                     " WHERE code = ? OR code_hash = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, code);
            pstmt.setString(2, hashCode(code));
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new DatabaseManager.CodeData(
                    rs.getInt("id"),
                    rs.getString("code"),
                    rs.getString("code_hash"),
                    rs.getInt("uses"),
                    rs.getInt("used_count"),
                    rs.getString("created_by"),
                    rs.getTimestamp("created_time"),
                    rs.getBoolean("is_active"),
                    rs.getTimestamp("last_used"),
                    rs.getString("reward_commands"),
                    rs.getTimestamp("expiration_time"),
                    rs.getInt("validity_days")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 更新兑换码使用次数
     */
    public boolean updateCodeUses(int codeId, int newUses) {
        String sql = "UPDATE " + getTableName("exchange_codes") + " SET uses = ? WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, newUses);
            pstmt.setInt(2, codeId);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 更新兑换码有效期
     */
    public boolean updateCodeValidity(int codeId, int validityDays) {
        String sql = "UPDATE " + getTableName("exchange_codes") + " SET validity_days = ?, expiration_time = ? WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, validityDays);
            if (validityDays == -1) {
                pstmt.setNull(2, java.sql.Types.TIMESTAMP);
            } else {
                long expirationMillis = System.currentTimeMillis() + (long) validityDays * 24 * 60 * 60 * 1000;
                pstmt.setTimestamp(2, new java.sql.Timestamp(expirationMillis));
            }
            pstmt.setInt(3, codeId);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 增加已使用次数
     */
    public boolean incrementUsedCount(int codeId) {
        String sql = "UPDATE " + getTableName("exchange_codes") + 
                     " SET used_count = used_count + 1, last_used = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, codeId);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 停用兑换码
     */
    public boolean deactivateCode(int codeId) {
        String sql = "UPDATE " + getTableName("exchange_codes") + " SET is_active = 0 WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, codeId);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 激活兑换码
     */
    public boolean activateCode(int codeId) {
        String sql = "UPDATE " + getTableName("exchange_codes") + " SET is_active = 1 WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, codeId);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 记录使用历史
     */
    public void recordUsage(int codeId, UUID playerUuid, String playerName) {
        String sql = "INSERT INTO " + getTableName("code_usage") + 
                     " (code_id, player_uuid, player_name) VALUES (?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, codeId);
            pstmt.setString(2, playerUuid.toString());
            pstmt.setString(3, playerName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取所有兑换码
     */
    public List<DatabaseManager.CodeData> getAllCodes() {
        List<DatabaseManager.CodeData> codes = new ArrayList<>();
        String sql = "SELECT * FROM " + getTableName("exchange_codes") + " ORDER BY created_time DESC";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                codes.add(new DatabaseManager.CodeData(
                    rs.getInt("id"),
                    rs.getString("code"),
                    rs.getString("code_hash"),
                    rs.getInt("uses"),
                    rs.getInt("used_count"),
                    rs.getString("created_by"),
                    rs.getTimestamp("created_time"),
                    rs.getBoolean("is_active"),
                    rs.getTimestamp("last_used"),
                    rs.getString("reward_commands"),
                    rs.getTimestamp("expiration_time"),
                    rs.getInt("validity_days")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return codes;
    }

    /**
     * 删除兑换码
     */
    public boolean deleteCode(int codeId) {
        String sql = "DELETE FROM " + getTableName("exchange_codes") + " WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, codeId);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 获取使用历史
     */
    public List<DatabaseManager.CodeUsage> getUsageHistory(int codeId) {
        List<DatabaseManager.CodeUsage> history = new ArrayList<>();
        String sql = "SELECT * FROM " + getTableName("code_usage") + 
                     " WHERE code_id = ? ORDER BY used_time DESC";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, codeId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                history.add(new DatabaseManager.CodeUsage(
                    rs.getInt("id"),
                    rs.getInt("code_id"),
                    rs.getString("player_uuid"),
                    rs.getString("player_name"),
                    rs.getTimestamp("used_time")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return history;
    }

    /**
     * 备份数据到 SQLite
     */
    public void backupTo(SQLiteDatabaseHandler target) {
        List<DatabaseManager.CodeData> codes = getAllCodes();
        for (DatabaseManager.CodeData code : codes) {
            target.saveCode(code.code, code.codeHash, code.uses, code.createdBy, code.rewardCommands,
                           code.expirationTime, code.validityDays);
        }
    }

    /**
     * 计算哈希
     */
    private String hashCode(String code) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(code.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return code;
        }
    }

    /**
     * 获取表名（带前缀）
     */
    private String getTableName(String baseName) {
        return tablePrefix + baseName;
    }

    /**
     * 关闭数据库连接池
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    /**
     * 检查连接是否有效
     */
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }
}
