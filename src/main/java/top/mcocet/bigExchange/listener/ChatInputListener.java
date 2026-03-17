package top.mcocet.bigExchange.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import top.mcocet.bigExchange.BigExchange;
import top.mcocet.bigExchange.manager.CodeManager;
import top.mcocet.bigExchange.manager.ConfigManager;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ChatInputListener implements Listener {
    private final CodeManager codeManager;
    private final ConfigManager configManager;
    private final Set<UUID> waitingForCode = new HashSet<>();

    public ChatInputListener(BigExchange plugin, CodeManager codeManager, ConfigManager configManager) {
        this.codeManager = codeManager;
        this.configManager = configManager;
    }

    /**
     * 添加等待输入兑换码的玩家
     */
    public void addWaitingPlayer(Player player) {
        waitingForCode.add(player.getUniqueId());
        player.sendMessage(configManager.getMessage("prefix") + 
                "§7请在聊天栏中输入兑换码（输入 cancel 取消）");
    }

    /**
     * 移除等待中的玩家
     */
    public void removeWaitingPlayer(Player player) {
        waitingForCode.remove(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
            
        if (!waitingForCode.contains(player.getUniqueId())) {
            return;
        }
    
        event.setCancelled(true); // 取消聊天消息
        String message = event.getMessage().trim();
    
        if (message.equalsIgnoreCase("cancel")) {
            removeWaitingPlayer(player);
            player.sendMessage(configManager.getMessage("prefix") + "§c 已取消输入");
            return;
        }
    
        // 处理兑换码
        boolean success = handleCodeInput(player, message);
            
        if (success) {
            removeWaitingPlayer(player);
        } else {
            // 验证失败，继续等待
            player.sendMessage(configManager.getMessage("prefix") + 
                    "§7 请重新输入或输入 cancel 取消");
        }
    }

    private boolean handleCodeInput(Player player, String code) {
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
                    // 显示详细的使用限制信息
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
            // 延迟 1 tick 发送消息，确保奖励命令先执行
            org.bukkit.Bukkit.getScheduler().runTaskLater(codeManager.getPlugin(), () -> {
                player.sendMessage(configManager.getMessage("code-redeemed"));
                
                // 显示剩余次数（如果不是无限次）
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
            
            // TODO: 在这里添加奖励发放逻辑
            // 可以集成经济插件、物品奖励等
            
            return true;
        } else {
            player.sendMessage(configManager.getMessage("error"));
            return false;
        }
    }
}
