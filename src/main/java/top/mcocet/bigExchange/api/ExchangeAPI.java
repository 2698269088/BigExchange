package top.mcocet.bigExchange.api;

import org.bukkit.entity.Player;
import top.mcocet.bigExchange.manager.CodeManager;
import top.mcocet.bigExchange.manager.DatabaseManager;

import java.util.List;
import java.util.UUID;

/**
 * BigExchange 公共 API
 * 供其他插件调用
 */
public class ExchangeAPI {
    private static ExchangeAPI instance;
    private final CodeManager codeManager;
    private final DatabaseManager databaseManager;

    public ExchangeAPI(CodeManager codeManager, DatabaseManager databaseManager) {
        this.codeManager = codeManager;
        this.databaseManager = databaseManager;
        instance = this;
    }

    public static ExchangeAPI getInstance() {
        return instance;
    }

    /**
     * 生成兑换码
     * @param uses 使用次数（-1 为无限次）
     * @param createdBy 创建者
     * @return 生成的兑换码
     */
    public String generateCode(int uses, String createdBy) {
        return codeManager.generateCode(uses, createdBy);
    }

    /**
     * 生成兑换码（带有效期）
     * @param uses 使用次数（-1 为无限次）
     * @param createdBy 创建者
     * @param validityDays 有效期天数（-1 表示永久）
     * @return 生成的兑换码
     */
    public String generateCode(int uses, String createdBy, int validityDays) {
        return codeManager.generateCode(uses, createdBy, null, validityDays);
    }

    /**
     * 验证兑换码
     * @param code 兑换码
     * @return 验证结果
     */
    public CodeManager.VerifyResult verifyCode(String code) {
        return codeManager.verifyCode(code);
    }

    /**
     * 使用兑换码
     * @param code 兑换码
     * @param playerUuid 玩家 UUID
     * @param playerName 玩家名称
     * @return 使用结果
     */
    public CodeManager.UseResult useCode(String code, UUID playerUuid, String playerName) {
        return codeManager.useCode(code, playerUuid, playerName);
    }

    /**
     * 检查兑换码格式是否有效
     * @param code 兑换码
     * @return 是否有效
     */
    public boolean isValidFormat(String code) {
        return codeManager.isValidFormat(code);
    }

    /**
     * 强制激活兑换码
     * @param codeId 兑换码 ID
     * @return 是否成功
     */
    public boolean activateCode(int codeId) {
        return codeManager.forceActivateCode(codeId);
    }

    /**
     * 停用兑换码
     * @param codeId 兑换码 ID
     * @return 是否成功
     */
    public boolean deactivateCode(int codeId) {
        return databaseManager.deactivateCode(codeId);
    }

    /**
     * 删除兑换码
     * @param codeId 兑换码 ID
     * @return 是否成功
     */
    public boolean deleteCode(int codeId) {
        return codeManager.deleteCode(codeId);
    }

    /**
     * 修改兑换码使用次数
     * @param codeId 兑换码 ID
     * @param newUses 新的次数（-1 为无限）
     * @return 是否成功
     */
    public boolean modifyCodeUses(int codeId, int newUses) {
        return codeManager.modifyCodeUses(codeId, newUses);
    }

    /**
     * 修改兑换码有效期
     * @param codeId 兑换码 ID
     * @param validityDays 新的有效期天数（-1 为永久）
     * @return 是否成功
     */
    public boolean modifyCodeValidity(int codeId, int validityDays) {
        return codeManager.modifyCodeValidity(codeId, validityDays);
    }

    /**
     * 获取所有兑换码
     * @return 兑换码列表
     */
    public List<DatabaseManager.CodeData> getAllCodes() {
        return codeManager.getAllCodes();
    }

    /**
     * 根据 ID 获取兑换码信息
     * @param codeId 兑换码 ID
     * @return 兑换码数据，不存在则返回 null
     */
    public DatabaseManager.CodeData getCodeById(int codeId) {
        for (DatabaseManager.CodeData code : getAllCodes()) {
            if (code.id == codeId) {
                return code;
            }
        }
        return null;
    }

    /**
     * 根据兑换码获取信息
     * @param code 兑换码
     * @return 兑换码数据，不存在则返回 null
     */
    public DatabaseManager.CodeData getCodeByString(String code) {
        return databaseManager.getCodeData(code);
    }

    /**
     * 获取兑换码使用历史
     * @param codeId 兑换码 ID
     * @return 使用历史记录
     */
    public List<DatabaseManager.CodeUsage> getUsageHistory(int codeId) {
        return codeManager.getUsageHistory(codeId);
    }

    /**
     * 获取兑换码剩余使用次数
     * @param codeId 兑换码 ID
     * @return 剩余次数（-1 表示无限）
     */
    public int getRemainingUses(int codeId) {
        DatabaseManager.CodeData code = getCodeById(codeId);
        return code != null ? code.getRemainingUses() : 0;
    }

    /**
     * 检查兑换码是否可用
     * @param codeId 兑换码 ID
     * @return 是否可用
     */
    public boolean isCodeAvailable(int codeId) {
        DatabaseManager.CodeData code = getCodeById(codeId);
        return code != null && code.isActive && code.hasUsesLeft();
    }

    /**
     * 检查兑换码是否已激活
     * @param codeId 兑换码 ID
     * @return 是否已激活
     */
    public boolean isCodeActive(int codeId) {
        DatabaseManager.CodeData code = getCodeById(codeId);
        return code != null && code.isActive;
    }

    /**
     * 检查兑换码是否为无限次数
     * @param codeId 兑换码 ID
     * @return 是否为无限次数
     */
    public boolean isUnlimited(int codeId) {
        DatabaseManager.CodeData code = getCodeById(codeId);
        return code != null && code.isUnlimited();
    }

    /**
     * 添加兑换码（直接保存已存在的兑换码）
     * @param code 兑换码
     * @param uses 使用次数
     * @param createdBy 创建者
     * @return 是否成功
     */
    public boolean addCode(String code, int uses, String createdBy) {
        if (databaseManager.codeExists(code)) {
            return false;
        }
        String codeHash = hashCode(code);
        databaseManager.saveCode(code, codeHash, uses, createdBy, "");
        return true;
    }

    /**
     * 删除兑换码（通过兑换码字符串）
     * @param code 兑换码
     * @return 是否成功
     */
    public boolean deleteCodeByString(String code) {
        DatabaseManager.CodeData codeData = databaseManager.getCodeData(code);
        if (codeData == null) {
            return false;
        }
        return databaseManager.deleteCode(codeData.id);
    }

    /**
     * 修改兑换码使用次数（通过兑换码字符串）
     * @param code 兑换码
     * @param newUses 新的次数
     * @return 是否成功
     */
    public boolean modifyCodeUsesByString(String code, int newUses) {
        DatabaseManager.CodeData codeData = databaseManager.getCodeData(code);
        if (codeData == null) {
            return false;
        }
        return databaseManager.updateCodeUses(codeData.id, newUses);
    }

    /**
     * 验证并激活兑换码
     * @param code 兑换码
     * @return 验证结果
     */
    public CodeManager.VerifyResult verifyAndActivate(String code) {
        CodeManager.VerifyResult result = codeManager.verifyCode(code);
        if (!result.isValid && result.status == CodeManager.VerifyResult.VerifyStatus.INACTIVE) {
            // 如果是未激活状态，尝试强制激活
            DatabaseManager.CodeData codeData = databaseManager.getCodeData(code);
            if (codeData != null) {
                boolean activated = databaseManager.activateCode(codeData.id);
                if (activated) {
                    return codeManager.verifyCode(code);
                }
            }
        }
        return result;
    }

    /**
     * 计算哈希（供内部使用）
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
}
