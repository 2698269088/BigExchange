package top.mcocet.bigExchange.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import top.mcocet.bigExchange.BigExchange;
import top.mcocet.bigExchange.manager.CodeManager;
import top.mcocet.bigExchange.manager.ConfigManager;
import top.mcocet.bigExchange.manager.LogManager;
import top.mcocet.bigExchange.util.AnvilGUIUtil;

import java.lang.reflect.Method;

public class FormListener implements Listener {
    private final BigExchange plugin;
    private final CodeManager codeManager;
    private final ConfigManager configManager;
    private final LogManager logManager;
    private final AnvilGUIUtil anvilGUIUtil;

    public FormListener(BigExchange plugin, CodeManager codeManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.codeManager = codeManager;
        this.configManager = configManager;
        this.logManager = new LogManager(plugin, configManager);
        this.anvilGUIUtil = new AnvilGUIUtil(plugin, configManager, codeManager);
    }

    /**
     * 向玩家发送兑换码输入表单（Java版使用AnvilGUI，基岩版使用 Floodgate 表单）
     */
    public void sendRedeemForm(Player player) {
        logManager.fine("收到玩家 " + player.getName() + " 的 /redeem 命令请求");
        
        // 检查是否为基岩版玩家
        if (isBedrockPlayer(player)) {
            // 基岩版玩家使用 Floodgate 表单
            if (configManager.isFormEnabled()) {
                sendGeyserForm(player);
            } else {
                sendChatForm(player);
            }
        } else {
            // Java 版玩家使用AnvilGUI
            if (configManager.isAnvilGUIEnabled()) {
                logManager.fine("Java 版玩家 " + player.getName() + "，打开 AnvilGUI 界面");
                anvilGUIUtil.openRedeemGUI(player);
            } else {
                logManager.fine("Java 版玩家 " + player.getName() + "，AnvilGUI 已禁用，使用聊天栏");
                sendChatForm(player);
            }
        }
    }
    
    /**
     * 检查玩家是否为基岩版玩家
     * @param player 玩家
     * @return 是否为基岩版玩家
     */
    private boolean isBedrockPlayer(Player player) {
        try {
            Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object apiInstance = floodgateApiClass.getMethod("getInstance").invoke(null);
            Boolean isFloodgatePlayer = (Boolean) floodgateApiClass.getMethod("isFloodgatePlayer", java.util.UUID.class)
                    .invoke(apiInstance, player.getUniqueId());
            return Boolean.TRUE.equals(isFloodgatePlayer);
        } catch (Exception e) {
            // Floodgate 不可用，默认不是基岩版玩家
            return false;
        }
    }

    /**
     * 发送聊天栏表单（Java 版或表单禁用时）
     */
    private void sendChatForm(Player player) {
        player.sendMessage(configManager.getMessage("prefix") + 
                "§6=== 兑换码兑换 ===");
        player.sendMessage("§7请在聊天栏中输入兑换码");
        player.sendMessage("§7格式：§bXXXXXX-XXXXXXXXXX");
    }
    
    /**
     * 发送 Geyser 国际版表单 (Floodgate)
     */
    private void sendGeyserForm(Player player) {
        logManager.fine("开始为玩家 " + player.getName() + " 发送 Geyser 表单");
        logManager.finer("玩家 UUID: " + player.getUniqueId());
        logManager.finer("玩家显示名称：" + player.getDisplayName());
        
        try {
            // 首先确认玩家是否为 Floodgate 玩家 (基岩版)
            logManager.fine("正在获取 FloodgateApi 实例...");
            Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object apiInstance = floodgateApiClass.getMethod("getInstance").invoke(null);
            logManager.fine("FloodgateApi 实例获取成功：" + (apiInstance != null));
            
            logManager.fine("正在检查玩家是否为 Floodgate 玩家...");
            Boolean isFloodgatePlayer = (Boolean) floodgateApiClass.getMethod("isFloodgatePlayer", java.util.UUID.class)
                    .invoke(apiInstance, player.getUniqueId());
            logManager.fine("玩家 " + player.getName() + " 是 Floodgate 玩家：" + isFloodgatePlayer);
            
            if (!Boolean.TRUE.equals(isFloodgatePlayer)) {
                // 不是 Floodgate 玩家，使用聊天栏
                logManager.fine("玩家 " + player.getName() + " 不是基岩版玩家，使用聊天栏");
                logManager.finer("UUID 前缀检查：" + player.getUniqueId().toString().substring(0, 8));
                sendChatForm(player);
                return;
            }
            
            logManager.fine("检测到基岩版玩家：" + player.getName() + "，准备发送表单");
            
            // 使用反射创建 Cumulus 表单，避免直接依赖
            logManager.fine("正在加载 Cumulus 表单类...");
            Class<?> customFormClass = Class.forName("org.geysermc.cumulus.form.CustomForm");
            logManager.fine("Cumulus 表单类加载成功");
            
            // 尝试多种方式创建表单构建器（兼容不同版本的 Cumulus）
            Object formBuilder;
            try {
                // 新版 Cumulus (2.0+) - 使用静态方法 builder()
                logManager.fine("尝试使用新版 Cumulus API (builder() 方法)...");
                formBuilder = Class.forName("org.geysermc.cumulus.form.CustomForm$Builder")
                        .getMethod("builder")
                        .invoke(null);
                logManager.fine("使用新版 Cumulus API 成功");
            } catch (NoSuchMethodException e) {
                // 旧版 Cumulus - 使用 CustomForm.builder()
                logManager.fine("新版 API 不可用，尝试旧版 Cumulus API...");
                formBuilder = customFormClass.getMethod("builder").invoke(null);
                logManager.fine("使用旧版 Cumulus API 成功");
            }
            logManager.fine("表单构建器创建成功");
            
            // 设置标题和输入框
            logManager.fine("正在设置表单标题和输入框...");
            formBuilder.getClass().getMethod("title", String.class)
                    .invoke(formBuilder, configManager.getFormRedeemTitle());
            logManager.finer("表单标题：" + configManager.getFormRedeemTitle());
            
            formBuilder.getClass().getMethod("input", String.class, String.class, String.class)
                    .invoke(formBuilder, "兑换码", configManager.getFormRedeemPlaceholder(), "");
            logManager.finer("表单输入框占位符：" + configManager.getFormRedeemPlaceholder());
            
            // 设置响应处理
            logManager.fine("正在设置表单响应处理器...");
            formBuilder.getClass().getMethod("validResultHandler", java.util.function.Consumer.class)
                    .invoke(formBuilder, (java.util.function.Consumer<?>) response -> {
                try {
                    logManager.finer("收到表单响应...");
                    com.google.gson.JsonArray responses = (com.google.gson.JsonArray) 
                        response.getClass().getMethod("getResponses").invoke(response);
                    if (responses != null && responses.size() > 0) {
                        String code = responses.get(0).getAsString();
                        logManager.fine("玩家输入的兑换码：" + code);
                        if (code != null && !code.trim().isEmpty()) {
                            handleCodeInput(player, code.trim());
                        } else {
                            player.sendMessage(configManager.getMessage("prefix") + "§c请输入兑换码!");
                        }
                    } else {
                        logManager.warning("表单响应为空");
                    }
                } catch (Exception e) {
                    logManager.severe("处理表单响应失败：" + e.getMessage());
                    logManager.severe("异常类型：" + e.getClass().getName());
                    e.printStackTrace();
                }
            });
            
            // 设置关闭处理
            logManager.fine("正在设置表单关闭处理器...");
            formBuilder.getClass().getMethod("closedOrInvalidResultHandler", Runnable.class)
                    .invoke(formBuilder, (Runnable) () -> {
                        logManager.fine("玩家 " + player.getName() + " 关闭了表单");
                        player.sendMessage(configManager.getMessage("prefix") + "§7已取消兑换");
                    });
            
            // 构建表单
            logManager.fine("正在构建表单对象...");
            Object form = formBuilder.getClass().getMethod("build").invoke(formBuilder);
            logManager.fine("表单构建成功，对象类型：" + (form != null ? form.getClass().getName() : "null"));
            
            // 获取 FloodgatePlayer 对象并发送表单
            logManager.fine("正在获取 FloodgatePlayer 对象...");
            Object floodgatePlayerObj = floodgateApiClass.getMethod("getPlayer", java.util.UUID.class)
                    .invoke(apiInstance, player.getUniqueId());
            logManager.fine("FloodgatePlayer 对象获取成功：" + (floodgatePlayerObj != null));
            logManager.finer("FloodgatePlayer 对象类型：" + (floodgatePlayerObj != null ? floodgatePlayerObj.getClass().getName() : "null"));
            
            if (floodgatePlayerObj != null) {
                // 发送表单给 Floodgate 玩家
                logManager.fine("正在向玩家 " + player.getName() + " 发送表单...");
                
                // 尝试多种方式发送表单（兼容不同版本的 Floodgate API）
                boolean sent = false;
                
                // 方式 1: 直接调用 sendForm(CustomForm)
                try {
                    logManager.fine("尝试方式 1: sendForm(CustomForm)...");
                    Method sendFormMethod = floodgatePlayerObj.getClass().getMethod("sendForm", customFormClass);
                    logManager.finer("找到方法：" + sendFormMethod);
                    sendFormMethod.invoke(floodgatePlayerObj, form);
                    sent = true;
                    logManager.fine("表单发送成功！玩家：" + player.getName());
                } catch (NoSuchMethodException e) {
                    logManager.fine("方式 1 不可用，尝试其他方式...");
                }
                
                // 方式 2: 尝试查找其他可能的方法签名
                if (!sent) {
                    try {
                        logManager.fine("尝试方式 2: 查找 Form 接口...");
                        // 查找所有名为 sendForm 的方法
                        Method[] methods = floodgatePlayerObj.getClass().getMethods();
                        for (Method method : methods) {
                            if (method.getName().equals("sendForm") && method.getParameterCount() == 1) {
                                Class<?> paramType = method.getParameterTypes()[0];
                                logManager.finer("找到候选方法：sendForm(" + paramType.getName() + ")");
                                if (paramType.isInstance(form)) {
                                    logManager.fine("使用匹配的方法：sendForm(" + paramType.getName() + ")");
                                    method.invoke(floodgatePlayerObj, form);
                                    sent = true;
                                    logManager.fine("表单发送成功！玩家：" + player.getName());
                                    break;
                                }
                            }
                        }
                    } catch (Exception e2) {
                        logManager.fine("方式 2 失败：" + e2.getMessage());
                    }
                }
                
                // 如果所有方式都失败
                if (!sent) {
                    logManager.severe("无法找到合适的 sendForm 方法发送表单");
                    logManager.severe("FloodgatePlayer 类：" + floodgatePlayerObj.getClass().getName());
                    logManager.severe("Form 对象类：" + (form != null ? form.getClass().getName() : "null"));
                    logManager.fine("可用的 sendForm 方法列表:");
                    
                    // 列出所有可用的 sendForm 方法用于调试
                    Method[] methods = floodgatePlayerObj.getClass().getMethods();
                    for (Method method : methods) {
                        if (method.getName().equals("sendForm")) {
                            StringBuilder params = new StringBuilder();
                            for (Class<?> param : method.getParameterTypes()) {
                                if (params.length() > 0) params.append(", ");
                                params.append(param.getSimpleName());
                            }
                            logManager.fine("  - sendForm(" + params + ")");
                        }
                    }
                    
                    sendChatForm(player);
                }
            } else {
                logManager.severe("无法获取 FloodgatePlayer 对象：" + player.getName());
                logManager.severe("可能原因：玩家已下线或 Floodgate 数据未正确加载");
                sendChatForm(player);
            }
        } catch (Exception e) {
            logManager.severe("发送 Geyser 表单时发生严重错误：" + e.getMessage());
            logManager.severe("错误类型：" + e.getClass().getName());
            logManager.severe("堆栈跟踪:");
            e.printStackTrace();
            if (configManager.isFormEnabled()) {
                sendChatForm(player);
            }
        }
    }
    
    /**
     * 处理玩家输入的兑换码
     */
    public boolean handleCodeInput(Player player, String code) {
        // 验证格式
        if (!codeManager.isValidFormat(code)) {
            player.sendMessage(configManager.getMessage("code-invalid"));
            return false;
        }

        // 验证兑换码
        CodeManager.VerifyResult result = codeManager.verifyCode(code);
        
        if (!result.isValid) {
            switch (result.status) {
                case NOT_FOUND -> player.sendMessage(configManager.getMessage("code-invalid"));
                case INACTIVE -> player.sendMessage(configManager.getMessage("code-expired"));
                case NO_USES_LEFT -> {
                    if (result.codeData != null) {
                        java.util.Map<String, String> placeholders = new java.util.HashMap<>();
                        placeholders.put("%used%", String.valueOf(result.codeData.usedCount));
                        placeholders.put("%total%", result.codeData.isUnlimited() ? "∞" : String.valueOf(result.codeData.uses));
                        player.sendMessage(configManager.getMessage("code-limit-reached", placeholders));
                    } else {
                        player.sendMessage(configManager.getMessage("code-no-uses"));
                    }
                }
                default -> player.sendMessage(configManager.getMessage("code-invalid"));
            }
            return false;
        }

        // 使用兑换码
        CodeManager.UseResult useResult = codeManager.useCode(code, player.getUniqueId(), player.getName());
        
        if (useResult.success) {
            // 延迟发送消息，确保奖励命令先执行
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage(configManager.getMessage("code-redeemed"));
                
                if (useResult.codeData != null && !useResult.codeData.isUnlimited()) {
                    int remaining = useResult.codeData.getRemainingUses();
                    if (remaining > 0) {
                        java.util.Map<String, String> placeholders = new java.util.HashMap<>();
                        placeholders.put("%remaining%", String.valueOf(remaining));
                        placeholders.put("%total%", String.valueOf(useResult.codeData.uses));
                        player.sendMessage(configManager.getMessage("code-remaining-uses", placeholders));
                    }
                }
            }, 1L);
            
            return true;
        } else {
            player.sendMessage(configManager.getMessage("error"));
            return false;
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // 可选：新玩家加入时提示
    }
}
