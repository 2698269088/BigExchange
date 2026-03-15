package top.mcocet.bigExchange.manager;

import top.mcocet.bigExchange.BigExchange;

import java.security.SecureRandom;
import java.util.Random;

public class CodeManager {
    private final BigExchange plugin;
    private final DatabaseManager databaseManager;
    private final ConfigManager configManager;
    private final LogManager logManager;
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private final SecureRandom secureRandom;

    public CodeManager(BigExchange plugin, DatabaseManager databaseManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.configManager = configManager;
        this.logManager = new LogManager(plugin, configManager);
        this.secureRandom = new SecureRandom();
    }

    /**
     * 获取插件实例
     * @return 插件实例
     */
    public BigExchange getPlugin() {
        return plugin;
    }

    /**
     * 生成随机兑换码
     * 格式：6 位 -10 位，共 16 位字符（包含大小写字母和数字）
     * @param uses 使用次数（-1 为无限次）
     * @param createdBy 创建者
     * @param rewardCommands 奖励命令（多个命令用分号分隔）
     * @param validityDays 有效期天数（-1 表示永久）
     * @return 生成的兑换码
     */
    public String generateCode(int uses, String createdBy, String rewardCommands, int validityDays) {
        int firstLength = configManager.getCodeFirstLength(); // 默认 6
        int secondLength = configManager.getCodeSecondLength(); // 默认 10
        String separator = configManager.getSeparator(); // 默认 "-"

        String code;
        do {
            StringBuilder sb = new StringBuilder();
            
            // 生成第一部分
            for (int i = 0; i < firstLength; i++) {
                sb.append(CHARACTERS.charAt(secureRandom.nextInt(CHARACTERS.length())));
            }
            
            // 添加分隔符
            sb.append(separator);
            
            // 生成第二部分
            for (int i = 0; i < secondLength; i++) {
                sb.append(CHARACTERS.charAt(secureRandom.nextInt(CHARACTERS.length())));
            }
            
            code = sb.toString();
        } while (databaseManager.codeExists(code)); // 确保唯一性

        // 计算过期时间
        java.sql.Timestamp expirationTime = null;
        if (validityDays != -1) {
            long expirationMillis = System.currentTimeMillis() + (long) validityDays * 24 * 60 * 60 * 1000;
            expirationTime = new java.sql.Timestamp(expirationMillis);
        }

        // 保存兑换码到数据库
        String codeHash = hashCode(code);
        databaseManager.saveCode(code, codeHash, uses, createdBy, rewardCommands, expirationTime, validityDays);

        logManager.info("生成新兑换码：" + code + " (可用次数：" + (uses == -1 ? "无限" : uses) + 
                       ", 有效期：" + (validityDays == -1 ? "永久" : validityDays + "天") + 
                       ", 奖励命令：" + rewardCommands + ")");
        return code;
    }

    /**
     * 生成随机兑换码
     */
    public String generateCode(int uses, String createdBy) {
        return generateCode(uses, createdBy, null, configManager.getDefaultValidityDays());
    }

    /**
     * 验证兑换码
     * @param code 兑换码
     * @return 验证结果
     */
    public VerifyResult verifyCode(String code) {
        // 检查格式
        if (!isValidFormat(code)) {
            return new VerifyResult(false, VerifyResult.VerifyStatus.INVALID_FORMAT);
        }

        // 从数据库获取数据
        DatabaseManager.CodeData codeData = databaseManager.getCodeData(code);
        
        if (codeData == null) {
            return new VerifyResult(false, VerifyResult.VerifyStatus.NOT_FOUND);
        }

        // 检查是否激活
        if (!codeData.isActive) {
            return new VerifyResult(false, VerifyResult.VerifyStatus.INACTIVE, codeData);
        }

        // 检查是否过期
        if (codeData.isExpired()) {
            return new VerifyResult(false, VerifyResult.VerifyStatus.EXPIRED, codeData);
        }

        // 检查剩余次数
        if (!codeData.hasUsesLeft()) {
            return new VerifyResult(false, VerifyResult.VerifyStatus.NO_USES_LEFT, codeData);
        }

        // 验证哈希
        String inputHash = hashCode(code);
        if (!inputHash.equals(codeData.codeHash)) {
            return new VerifyResult(false, VerifyResult.VerifyStatus.INVALID_HASH);
        }

        return new VerifyResult(true, VerifyResult.VerifyStatus.VALID, codeData);
    }

    /**
     * 使用兑换码
     * @param code 兑换码
     * @param playerUuid 玩家 UUID
     * @param playerName 玩家名称
     * @return 使用结果
     */
    public UseResult useCode(String code, java.util.UUID playerUuid, String playerName) {
        VerifyResult verifyResult = verifyCode(code);
        
        if (!verifyResult.isValid) {
            return new UseResult(false, verifyResult.status, null);
        }

        DatabaseManager.CodeData codeData = verifyResult.codeData;
        
        // 记录使用
        databaseManager.incrementUsedCount(codeData.id);
        databaseManager.recordUsage(codeData.id, playerUuid, playerName);
        
        // 重新读取最新的兑换码数据（因为 usedCount 已经改变）
        DatabaseManager.CodeData updatedCodeData = databaseManager.getCodeData(code);
        
        // 如果次数用完，自动停用
        if (updatedCodeData != null && !updatedCodeData.isUnlimited() && updatedCodeData.usedCount >= updatedCodeData.uses) {
            databaseManager.deactivateCode(codeData.id);
        }

        // 执行奖励命令
        if (updatedCodeData != null && updatedCodeData.rewardCommands != null && !updatedCodeData.rewardCommands.isEmpty()) {
            executeRewardCommands(updatedCodeData.rewardCommands, playerName);
        }

        return new UseResult(true, VerifyResult.VerifyStatus.VALID, updatedCodeData);
    }

    /**
     * 执行奖励命令
     * @param rewardCommands 奖励命令（分号分隔）
     * @param playerName 玩家名称
     */
    private void executeRewardCommands(String rewardCommands, String playerName) {
        if (rewardCommands == null || rewardCommands.isEmpty()) {
            return;
        }

        // 按分号分割多个命令
        String[] commands = rewardCommands.split(";");
        for (String cmd : commands) {
            String command = cmd.trim();
            if (command.isEmpty()) continue;

            // 替换占位符 {player}
            final String finalCommand = command.replace("{player}", playerName);

            // 异步执行命令
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    plugin.getServer().dispatchCommand(
                        plugin.getServer().getConsoleSender(), 
                        finalCommand
                    );
                } catch (Exception e) {
                    logManager.warning("执行奖励命令失败：" + finalCommand + " - " + e.getMessage());
                }
            });
        }
    }

    /**
     * 强制激活兑换码（管理员功能）
     * @param codeId 兑换码 ID
     * @return 是否成功
     */
    public boolean forceActivateCode(int codeId) {
        return databaseManager.activateCode(codeId);
    }

    /**
     * 修改兑换码使用次数
     * @param codeId 兑换码 ID
     * @param newUses 新的次数（-1 为无限）
     * @return 是否成功
     */
    public boolean modifyCodeUses(int codeId, int newUses) {
        return databaseManager.updateCodeUses(codeId, newUses);
    }

    /**
     * 修改兑换码有效期
     * @param codeId 兑换码 ID
     * @param validityDays 新的有效期天数（-1 为永久）
     * @return 是否成功
     */
    public boolean modifyCodeValidity(int codeId, int validityDays) {
        return databaseManager.updateCodeValidity(codeId, validityDays);
    }

    /**
     * 删除兑换码
     * @param codeId 兑换码 ID
     * @return 是否成功
     */
    public boolean deleteCode(int codeId) {
        return databaseManager.deleteCode(codeId);
    }

    /**
     * 检查兑换码格式
     * @param code 兑换码
     * @return 是否符合格式
     */
    public boolean isValidFormat(String code) {
        if (code == null || code.isEmpty()) {
            return false;
        }

        int firstLength = configManager.getCodeFirstLength();
        int secondLength = configManager.getCodeSecondLength();
        String separator = configManager.getSeparator();

        // 检查分隔符
        int separatorIndex = code.indexOf(separator);
        if (separatorIndex == -1 || separatorIndex != firstLength) {
            return false;
        }

        // 检查总长度
        int expectedTotalLength = firstLength + separator.length() + secondLength;
        if (code.length() != expectedTotalLength) {
            return false;
        }

        // 检查字符合法性
        String[] parts = code.split(separator);
        if (parts.length != 2) {
            return false;
        }

        // 检查第一部分
        if (parts[0].length() != firstLength) {
            return false;
        }
        for (char c : parts[0].toCharArray()) {
            if (CHARACTERS.indexOf(c) == -1) {
                return false;
            }
        }

        // 检查第二部分
        if (parts[1].length() != secondLength) {
            return false;
        }
        for (char c : parts[1].toCharArray()) {
            if (CHARACTERS.indexOf(c) == -1) {
                return false;
            }
        }

        return true;
    }

    /**
     * 获取所有兑换码
     * @return 兑换码列表
     */
    public java.util.List<DatabaseManager.CodeData> getAllCodes() {
        return databaseManager.getAllCodes();
    }

    /**
     * 获取兑换码使用历史
     * @param codeId 兑换码 ID
     * @return 使用历史记录
     */
    public java.util.List<DatabaseManager.CodeUsage> getUsageHistory(int codeId) {
        return databaseManager.getUsageHistory(codeId);
    }

    /**
     * 计算兑换码哈希（用于加密认证）
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

    // 验证结果类
    public static class VerifyResult {
        public final boolean isValid;
        public final VerifyStatus status;
        public final DatabaseManager.CodeData codeData;

        public VerifyResult(boolean isValid, VerifyStatus status) {
            this(isValid, status, null);
        }

        public VerifyResult(boolean isValid, VerifyStatus status, DatabaseManager.CodeData codeData) {
            this.isValid = isValid;
            this.status = status;
            this.codeData = codeData;
        }

        public enum VerifyStatus {
            VALID,              // 有效
            INVALID_FORMAT,     // 格式错误
            NOT_FOUND,          // 不存在
            INACTIVE,           // 未激活/已停用
            NO_USES_LEFT,       // 无剩余次数
            INVALID_HASH,       // 哈希验证失败
            EXPIRED             // 已过期
        }
    }

    // 使用结果类
    public static class UseResult {
        public final boolean success;
        public final VerifyResult.VerifyStatus status;
        public final DatabaseManager.CodeData codeData;

        public UseResult(boolean success, VerifyResult.VerifyStatus status, DatabaseManager.CodeData codeData) {
            this.success = success;
            this.status = status;
            this.codeData = codeData;
        }
    }
}
