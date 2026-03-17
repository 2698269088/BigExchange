package top.mcocet.bigExchange.manager.craftconomy3;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.command.CommandSender;
import top.mcocet.bigExchange.BigExchange;

import java.sql.*;

/**
 * Craftconomy3 MySQL 数据库处理器
 * 完全仿照 Craftconomy3 的数据库逻辑，用于读取和扣除玩家余额
 * 支持 MySQL 5.7+ 版本
 */
public class Craftconomy3MySQLHandler {
    private final BigExchange plugin;
    private HikariDataSource dataSource;
    private final String tablePrefix;
    private final String currencyName;
    private final String worldGroup;
    
    // 表定义（与 Craftconomy3 一致）
    private static final String ACCOUNT_TABLE = "account";
    private static final String BALANCE_TABLE = "balance";
    private static final String CURRENCY_TABLE = "currency";
    
    public Craftconomy3MySQLHandler(BigExchange plugin, String mysqlHost, int mysqlPort, 
                                     String mysqlDatabase, String mysqlUsername, 
                                     String mysqlPassword, String mysqlPrefix,
                                     String currencyName, String worldGroup) {
        this.plugin = plugin;
        this.tablePrefix = mysqlPrefix != null ? mysqlPrefix : "";
        this.currencyName = currencyName;
        this.worldGroup = worldGroup;
        
        try {
            initializeDataSource(mysqlHost, mysqlPort, mysqlDatabase, mysqlUsername, mysqlPassword);
        } catch (Exception e) {
            plugin.getLogger().severe("初始化 Craftconomy3 MySQL 数据库失败：" + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 初始化数据源
     */
    private void initializeDataSource(String host, int port, String database, 
                                     String username, String password) throws Exception {
        // 加载 MySQL 驱动
        Class.forName("com.mysql.jdbc.Driver");
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=false&characterEncoding=utf-8&useUnicode=true", 
            host, port, database));
        config.setUsername(username);
        config.setPassword(password);
        
        // 连接池配置（仿照 Craftconomy3）
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(5000);
        config.setMaxLifetime(1800000);
        
        // MySQL 5.7 特定配置
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        
        dataSource = new HikariDataSource(config);
        
        // 测试连接
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery("SELECT 1");
            plugin.getLogger().info("Craftconomy3 MySQL 数据库连接成功");
        }
    }
    
    /**
     * 获取玩家的账户余额
     * @param playerName 玩家名称
     * @return 玩家余额
     */
    public double getPlayerBalance(String playerName) {
        return getPlayerBalance(playerName, worldGroup, currencyName);
    }
    
    /**
     * 获取玩家的账户余额（指定世界组和货币）
     * @param playerName 玩家名称
     * @param worldGroup 世界组
     * @param currency 货币名称
     * @return 玩家余额
     */
    public double getPlayerBalance(String playerName, String worldGroup, String currency) {
        if (dataSource == null || dataSource.isClosed()) {
            plugin.getLogger().severe("[Craftconomy3] 数据库连接未初始化或已关闭");
            return 0.0;
        }
        
        String sql = """
            SELECT b.balance FROM %s b
            INNER JOIN %s a ON b.username_id = a.id
            WHERE a.name = ? AND b.worldName = ? AND b.currency_id = ?
            """.formatted(getTableName(BALANCE_TABLE), getTableName(ACCOUNT_TABLE));
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerName);
            pstmt.setString(2, worldGroup);
            pstmt.setString(3, currency);
            
            plugin.getLogger().fine("[Craftconomy3] 查询玩家 " + playerName + " 余额");
            
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                double balance = rs.getDouble("balance");
                plugin.getLogger().fine("[Craftconomy3] 玩家 " + playerName + " 余额：" + balance);
                return balance;
            } else {
                plugin.getLogger().fine("[Craftconomy3] 未找到玩家 " + playerName + " 的余额记录");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Craftconomy3] 查询余额失败：" + e.getMessage());
            e.printStackTrace();
        }
        
        return 0.0;
    }
    
    /**
     * 检查玩家是否有足够的余额
     * @param playerName 玩家名称
     * @param amount 金额
     * @return 是否足够
     */
    public boolean hasEnough(String playerName, double amount) {
        return getPlayerBalance(playerName, worldGroup, currencyName) >= amount;
    }
    
    /**
     * 检查玩家是否有账户
     * @param playerName 玩家名称
     * @return 是否有账户
     */
    public boolean hasAccount(String playerName) {
        return hasAccount(playerName, worldGroup, currencyName);
    }
    
    /**
     * 检查玩家是否有账户（指定世界组和货币）
     * @param playerName 玩家名称
     * @param worldGroup 世界组
     * @param currency 货币名称
     * @return 是否有账户
     */
    public boolean hasAccount(String playerName, String worldGroup, String currency) {
        if (dataSource == null || dataSource.isClosed()) {
            return false;
        }
        
        String sql = """
            SELECT COUNT(*) as count FROM %s b
            INNER JOIN %s a ON b.username_id = a.id
            WHERE a.name = ? AND b.worldName = ? AND b.currency_id = ?
            """.formatted(getTableName(BALANCE_TABLE), getTableName(ACCOUNT_TABLE));
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerName);
            pstmt.setString(2, worldGroup);
            pstmt.setString(3, currency);
            
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                int count = rs.getInt("count");
                return count > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Craftconomy3] 检查账户失败：" + e.getMessage());
            e.printStackTrace();
        }
        
        return false;
    }
    
    /**
     * 从玩家账户扣除金额
     * @param playerName 玩家名称
     * @param amount 金额
     * @return 是否成功
     */
    public boolean withdrawPlayer(String playerName, double amount) {
        if (dataSource == null || dataSource.isClosed()) {
            plugin.getLogger().severe("[Craftconomy3] 数据库连接未初始化或已关闭");
            return false;
        }
        
        plugin.getLogger().fine("[Craftconomy3] 开始扣除玩家 " + playerName + " " + amount + " 元");
        
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false); // 开启事务
            
            // 获取账户 ID
            String accountIdSql = "SELECT id FROM " + getTableName(ACCOUNT_TABLE) + " WHERE name = ? AND bank = ?";
            int accountId;
            try (PreparedStatement pstmt = conn.prepareStatement(accountIdSql)) {
                pstmt.setString(1, playerName);
                pstmt.setBoolean(2, false); // 不是银行账户
                ResultSet rs = pstmt.executeQuery();
                if (!rs.next()) {
                    plugin.getLogger().severe("[Craftconomy3] 未找到玩家账户：" + playerName);
                    return false;
                }
                accountId = rs.getInt("id");
                plugin.getLogger().fine("[Craftconomy3] 找到玩家账户 ID: " + accountId);
            }
            
            // 检查余额是否足够
            double currentBalance = getPlayerBalance(playerName);
            plugin.getLogger().fine("[Craftconomy3] 玩家当前余额：" + currentBalance + ", 需要扣除：" + amount);
            
            if (currentBalance < amount) {
                plugin.getLogger().warning("[Craftconomy3] 玩家 " + playerName + " 余额不足，当前：" + currentBalance + "，需要：" + amount);
                conn.rollback();
                return false;
            }
            
            // 更新余额
            String updateSql = """
                UPDATE %s SET balance = balance - ?
                WHERE username_id = ? AND worldName = ? AND currency_id = ?
                """.formatted(getTableName(BALANCE_TABLE));
            
            try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                pstmt.setDouble(1, amount);
                pstmt.setInt(2, accountId);
                pstmt.setString(3, worldGroup);
                pstmt.setString(4, currencyName);
                int rows = pstmt.executeUpdate();
                
                if (rows > 0) {
                    conn.commit();
                    plugin.getLogger().info("[Craftconomy3] 成功从玩家 " + playerName + " 扣除 " + amount + " (新余额：" + (currentBalance - amount) + ")");
                    return true;
                } else {
                    conn.rollback();
                    plugin.getLogger().severe("[Craftconomy3] 扣除余额失败，SQL 执行无影响行");
                    return false;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Craftconomy3] 扣款失败：" + e.getMessage());
            e.printStackTrace();
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            return false;
        }
    }
    
    /**
     * 检查数据库连接是否正常
     * @return 是否连接
     */
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }
    
    /**
     * 关闭数据库连接
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Craftconomy3 MySQL 数据库连接已关闭");
        }
    }
    
    /**
     * 获取表名（带前缀）
     */
    private String getTableName(String baseName) {
        return tablePrefix + baseName;
    }
    
    /**
     * 查询所有账户数据（所有世界组、所有玩家、所有货币）
     * @param sender 命令发送者
     * @param logManager 日志管理器
     */
    public void queryAllAccounts(CommandSender sender, top.mcocet.bigExchange.manager.LogManager logManager) {
        if (dataSource == null || dataSource.isClosed()) {
            sender.sendMessage("§8[§6BigExchange§8] §c 数据库连接未初始化或已关闭");
            return;
        }
        
        String sql = """
            SELECT a.name, b.worldName, b.currency_id, b.balance 
            FROM %s b
            INNER JOIN %s a ON b.username_id = a.id
            ORDER BY a.name, b.worldName, b.currency_id
            """.formatted(getTableName(BALANCE_TABLE), getTableName(ACCOUNT_TABLE));
        
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            int count = 0;
            String lastName = null;
            
            while (rs.next()) {
                String playerName = rs.getString("name");
                String worldName = rs.getString("worldName");
                String currency = rs.getString("currency_id");
                double balance = rs.getDouble("balance");
                
                // 如果玩家名称变化，显示分隔线
                if (lastName != null && !lastName.equals(playerName)) {
                    sender.sendMessage("§8[§6BigExchange§8] §7--------------------------------------");
                }
                lastName = playerName;
                
                sender.sendMessage("§8[§6BigExchange§8] §b" + playerName + 
                                 " §7| 世界组：§e" + worldName + 
                                 " §7| 货币：§e" + currency + 
                                 " §7| 余额：§a$" + balance);
                count++;
            }
            
            sender.sendMessage("§8[§6BigExchange§8] §7======================================");
            sender.sendMessage("§8[§6BigExchange§8] §7 共查询 §e" + count + " §7 条账户记录");
            logManager.info("全量查询 Craftconomy3 数据，共 " + count + " 条记录");
            
        } catch (SQLException e) {
            sender.sendMessage("§8[§6BigExchange§8] §c 查询失败：" + e.getMessage());
            logManager.severe("Craftconomy3 全量查询失败：" + e.getMessage());
            e.printStackTrace();
        }
    }
}
