package top.mcocet.bigExchange.manager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SQLite 数据库处理器
 */
public class SQLiteDatabaseHandler {
    private Connection connection;
    private final String dbPath;

    public SQLiteDatabaseHandler(String dbPath) {
        this.dbPath = dbPath;
    }

    /**
     * 初始化数据库连接
     */
    public void initialize() throws Exception {
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        createTables();
    }

    /**
     * 创建数据表
     */
    private void createTables() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS exchange_codes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code TEXT UNIQUE NOT NULL,
                    code_hash TEXT NOT NULL,
                    uses INTEGER DEFAULT -1,
                    used_count INTEGER DEFAULT 0,
                    created_by TEXT,
                    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    is_active BOOLEAN DEFAULT 1,
                    last_used TIMESTAMP,
                    reward_commands TEXT,
                    expiration_time TIMESTAMP,
                    validity_days INTEGER DEFAULT -1,
                    metadata TEXT
                )""";
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
        
        // 检查并添加 reward_commands 字段（兼容旧版本）
        addColumnIfNotExists("exchange_codes", "reward_commands", "TEXT");
        
        // 检查并添加 expiration_time 字段
        addColumnIfNotExists("exchange_codes", "expiration_time", "TIMESTAMP");
        
        // 检查并添加 validity_days 字段
        addColumnIfNotExists("exchange_codes", "validity_days", "INTEGER");
        
        String usageSql = """
                CREATE TABLE IF NOT EXISTS code_usage (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code_id INTEGER NOT NULL,
                    player_uuid TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    used_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (code_id) REFERENCES exchange_codes(id)
                )""";
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(usageSql);
        }
    }

    /**
     * 检查并添加字段（如果不存在）
     */
    private void addColumnIfNotExists(String tableName, String columnName, String columnType) throws SQLException {
        String checkSql = "PRAGMA table_info(" + tableName + ")";
        boolean columnExists = false;
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(checkSql)) {
            while (rs.next()) {
                if (columnName.equals(rs.getString("name"))) {
                    columnExists = true;
                    break;
                }
            }
        }
        
        if (!columnExists) {
            String alterSql = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnType;
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(alterSql);
            }
        }
    }

    /**
     * 保存兑换码
     */
    public void saveCode(String code, String codeHash, int uses, String createdBy, String rewardCommands,
                        java.sql.Timestamp expirationTime, int validityDays) {
        String sql = "INSERT INTO exchange_codes (code, code_hash, uses, created_by, reward_commands, expiration_time, validity_days) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
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
        String sql = "SELECT COUNT(*) FROM exchange_codes WHERE code = ? OR code_hash = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
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
        String sql = "SELECT * FROM exchange_codes WHERE code = ? OR code_hash = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
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
        String sql = "UPDATE exchange_codes SET uses = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
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
        try (PreparedStatement pstmt = connection.prepareStatement(
                "UPDATE exchange_codes SET validity_days = ?, expiration_time = ? WHERE id = ?")) {
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
     * 设置兑换码过期时间（从创建时间开始计算的天数）
     */
    public boolean setCodeExpiration(int codeId, int daysFromCreation) {
        try (PreparedStatement pstmt = connection.prepareStatement(
                "UPDATE exchange_codes SET validity_days = ?, expiration_time = DATE(created_time, ?) WHERE id = ?")) {
            pstmt.setInt(1, daysFromCreation);
            pstmt.setString(2, "+" + daysFromCreation + " days");
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
        String sql = "UPDATE exchange_codes SET used_count = used_count + 1, last_used = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
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
        String sql = "UPDATE exchange_codes SET is_active = 0 WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
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
        String sql = "UPDATE exchange_codes SET is_active = 1 WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
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
        String sql = "INSERT INTO code_usage (code_id, player_uuid, player_name) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
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
        String sql = "SELECT * FROM exchange_codes ORDER BY created_time DESC";
        try (Statement stmt = connection.createStatement();
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
        String sql = "DELETE FROM exchange_codes WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
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
        String sql = "SELECT * FROM code_usage WHERE code_id = ? ORDER BY used_time DESC";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
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
     * 备份数据到 MySQL
     */
    public void backupTo(MySQLDatabaseHandler target) {
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
     * 关闭数据库连接
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * 检查连接是否有效
     */
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
}
