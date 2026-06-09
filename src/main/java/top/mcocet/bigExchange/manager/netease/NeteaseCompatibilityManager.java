package top.mcocet.bigExchange.manager.netease;

import org.bukkit.entity.Player;
import top.mcocet.bigExchange.BigExchange;
import top.mcocet.bigExchange.manager.ConfigManager;
import top.mcocet.bigExchange.manager.LogManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 网易我的世界中国版兼容层管理器
 * 负责处理网易版本的表单发送和事件通知
 */
public class NeteaseCompatibilityManager {
    private final BigExchange plugin;
    private final ConfigManager configManager;
    private final LogManager logManager;
    
    // 存储等待响应的表单回调
    private final Map<UUID, Consumer<String>> pendingForms = new ConcurrentHashMap<>();
    
    // BaseAPI 实例（通过反射获取）
    private Object baseAPIInstance;
    private boolean baseAPIAvailable = false;
    
    public NeteaseCompatibilityManager(BigExchange plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.logManager = plugin.getLogManager();
        
        // 初始化时检查 BaseAPI 是否可用
        checkBaseAPIAvailability();
    }
    
    /**
     * 检查 BaseAPI 是否可用
     */
    private void checkBaseAPIAvailability() {
        try {
            Class<?> baseAPIClass = Class.forName("com.xigua.baseAPI.BaseAPI");
            baseAPIInstance = plugin.getServer().getPluginManager().getPlugin("BaseAPI");
            
            if (baseAPIInstance != null) {
                baseAPIAvailable = true;
                logManager.info("检测到 BaseAPI 插件，网易兼容层已启用");
            } else {
                logManager.warning("BaseAPI 插件未安装，网易表单功能将不可用");
            }
        } catch (ClassNotFoundException e) {
            logManager.warning("BaseAPI 类未找到，网易兼容层不可用");
            baseAPIAvailable = false;
        }
    }
    
    /**
     * 检查玩家是否为网易版玩家
     * @param player 玩家
     * @return 是否为网易版玩家
     */
    public boolean isNeteasePlayer(Player player) {
        if (!baseAPIAvailable) {
            return false;
        }
        
        try {
            // 通过 BaseAPI 的 PlayerManager 检查
            Class<?> baseAPIClass = Class.forName("com.xigua.baseAPI.BaseAPI");
            Object playerManager = baseAPIClass.getMethod("getPlayerManager").invoke(baseAPIInstance);
            
            if (playerManager != null) {
                // 如果 PlayerManager 中有缓存的 UID，说明是网易玩家
                Long uid = (Long) playerManager.getClass()
                    .getMethod("getCachedUid", org.bukkit.entity.Player.class)
                    .invoke(playerManager, player);
                
                return uid != null && uid > 0;
            }
        } catch (Exception e) {
            logManager.fine("检查网易玩家失败: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * 发送兑换码输入表单给网易版玩家
     * @param player 玩家
     * @param callback 表单响应回调
     */
    public void sendRedeemForm(Player player, Consumer<String> callback) {
        if (!baseAPIAvailable) {
            logManager.warning("BaseAPI 不可用，无法发送网易表单");
            return;
        }
        
        try {
            logManager.fine("开始为玩家 " + player.getName() + " 发送网易表单");
            
            // 保存回调
            pendingForms.put(player.getUniqueId(), callback);
            
            // 使用 BaseAPI 发送自定义表单
            sendCustomForm(player);
            
        } catch (Exception e) {
            logManager.severe("发送网易表单失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 发送自定义表单（使用 BaseAPI 的 Cumulus 表单系统）
     */
    private void sendCustomForm(Player player) throws Exception {
        // 加载 Cumulus 表单类
        Class<?> customFormClass = Class.forName("com.xigua.cumulus.form.CustomForm");
        
        // 创建表单构建器
        Object formBuilder = customFormClass.getMethod("builder").invoke(null);
        
        // 设置标题
        formBuilder.getClass().getMethod("title", String.class)
            .invoke(formBuilder, configManager.getFormRedeemTitle());
        
        // 获取配置的输入框数量（默认5个，最大20个）
        int inputCount = Math.max(1, Math.min(20, configManager.getConfig().getInt("form.redeem-input-count", 5)));
        
        // 添加输入框
        for (int i = 1; i <= inputCount; i++) {
            String label = (i == 1) ? "兑换码 " : "兑换码 " + i;
            formBuilder.getClass().getMethod("input", String.class, String.class, String.class)
                .invoke(formBuilder, label, configManager.getFormRedeemPlaceholder(), "");
        }
        
        // 设置响应处理
        formBuilder.getClass().getMethod("validResultHandler", java.util.function.Consumer.class)
            .invoke(formBuilder, (java.util.function.Consumer<?>) response -> {
                try {
                    com.google.gson.JsonArray responses = (com.google.gson.JsonArray) 
                        response.getClass().getMethod("getResponses").invoke(response);
                    
                    if (responses != null && responses.size() > 0) {
                        // 收集所有非空的兑换码
                        java.util.List<String> codes = new java.util.ArrayList<>();
                        for (int i = 0; i < responses.size(); i++) {
                            String code = responses.get(i).getAsString();
                            if (code != null && !code.trim().isEmpty()) {
                                codes.add(code.trim());
                            }
                        }
                        logManager.fine("玩家 " + player.getName() + " 输入的兑换码数量: " + codes.size());
                        
                        // 调用回调，将多个兑换码用空格分隔
                        Consumer<String> callback = pendingForms.remove(player.getUniqueId());
                        if (callback != null) {
                            callback.accept(String.join(" ", codes));
                        }
                    }
                } catch (Exception e) {
                    logManager.severe("处理网易表单响应失败: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        
        // 设置关闭处理
        formBuilder.getClass().getMethod("closedOrInvalidResultHandler", Runnable.class)
            .invoke(formBuilder, (Runnable) () -> {
                logManager.fine("玩家 " + player.getName() + " 关闭了表单");
                pendingForms.remove(player.getUniqueId());
                player.sendMessage(configManager.getMessage("prefix") + "§7已取消兑换");
            });
        
        // 构建表单
        Object form = formBuilder.getClass().getMethod("build").invoke(formBuilder);
        
        // 通过 BaseAPI 发送表单
        Class<?> baseAPIClass = Class.forName("com.xigua.baseAPI.BaseAPI");
        baseAPIClass.getMethod("sendForm", UUID.class, Class.forName("com.xigua.cumulus.form.Form"))
            .invoke(baseAPIInstance, player.getUniqueId(), form);
        
        logManager.fine("网易表单发送成功");
    }
    
    /**
     * 处理表单响应（供外部调用）
     * @param playerUuid 玩家 UUID
     * @param code 兑换码
     */
    public void handleFormResponse(UUID playerUuid, String code) {
        Consumer<String> callback = pendingForms.remove(playerUuid);
        if (callback != null) {
            callback.accept(code);
        }
    }
    
    /**
     * 清理玩家的待处理表单
     * @param playerUuid 玩家 UUID
     */
    public void cleanupPendingForm(UUID playerUuid) {
        pendingForms.remove(playerUuid);
    }
    
    /**
     * 检查 BaseAPI 是否可用
     * @return 是否可用
     */
    public boolean isBaseAPIAvailable() {
        return baseAPIAvailable;
    }
    
    /**
     * 获取 BaseAPI 实例（供外部调用）
     * @return BaseAPI 实例
     */
    public Object getBaseAPIInstance() {
        return baseAPIInstance;
    }
}