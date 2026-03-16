package top.mcocet.bigExchange.util;

import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import top.mcocet.bigExchange.BigExchange;
import top.mcocet.bigExchange.manager.CodeManager;
import top.mcocet.bigExchange.manager.ConfigManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AnvilGUI 工具类 - 为 Java版玩家提供图形化输入界面
 */
public class AnvilGUIUtil {
    
    private final BigExchange plugin;
    private final ConfigManager configManager;
    private final CodeManager codeManager;
    private final Map<UUID, Long> clickCooldowns; // 点击冷却时间
    
    public AnvilGUIUtil(BigExchange plugin, ConfigManager configManager, CodeManager codeManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.codeManager = codeManager;
        this.clickCooldowns = new HashMap<>();
    }
    
    /**
     * 打开兑换码输入界面
     * @param player 目标玩家
     */
    public void openRedeemGUI(Player player) {
        new AnvilGUI.Builder()
                .onClose(stateSnapshot -> {
                    // 玩家关闭界面
                    // stateSnapshot.getPlayer().sendMessage(configManager.getMessage("prefix") + "§7已取消兑换");
                })
                .onClick((slot, stateSnapshot) -> {
                    Player p = stateSnapshot.getPlayer();
                    UUID uuid = p.getUniqueId();
                    long currentTime = System.currentTimeMillis();
                    
                    // 检查冷却时间（防止快速点击）
                    if (clickCooldowns.containsKey(uuid)) {
                        long lastClick = clickCooldowns.get(uuid);
                        if (currentTime - lastClick < 500) { // 500ms 冷却
                            return Collections.emptyList();
                        }
                    }
                    clickCooldowns.put(uuid, currentTime);
                    
                    // 只处理输出槽的点击（玩家放入物品后点击）
                    if (slot == AnvilGUI.Slot.OUTPUT) {
                        String code = stateSnapshot.getText();
                        if (code != null && !code.trim().isEmpty()) {
                            // 延迟一点处理，让界面有机会更新
                            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                handleCodeInput(p, code.trim(), true); // true 表示完成后关闭界面
                            }, 2L);
                        } else {
                            p.sendMessage(configManager.getMessage("prefix") + "§c请输入兑换码!");
                        }
                    }
                    // 其他槽位的点击不处理（纸和屏障），避免 Spartan 报错
                    return Collections.emptyList();
                })
                .title("兑换码兑换")
                .itemLeft(new org.bukkit.inventory.ItemStack(Material.PAPER))
                .itemRight(new org.bukkit.inventory.ItemStack(Material.BARRIER))
                .plugin(plugin)
                .open(player);
    }
    
    /**
     * 处理玩家输入的兑换码
     * @param player 玩家
     * @param code 兑换码
     * @param closeAfterComplete 完成后是否关闭界面
     */
    private void handleCodeInput(Player player, String code, boolean closeAfterComplete) {
        // 验证格式
        if (!codeManager.isValidFormat(code)) {
            player.sendMessage(configManager.getMessage("code-invalid"));
            return;
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
            return;
        }
        
        // 使用兑换码
        CodeManager.UseResult useResult = codeManager.useCode(code, player.getUniqueId(), player.getName());
        
        if (useResult.success) {
            // 延迟发送消息，确保奖励命令先执行
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
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
                
                // 显示有效期信息
                if (useResult.codeData != null && useResult.codeData.hasValidity()) {
                    java.util.Map<String, String> placeholders = new java.util.HashMap<>();
                    placeholders.put("%remaining_time%", useResult.codeData.getFormattedRemainingTime());
                    player.sendMessage(configManager.getMessage("code-validity-remaining", placeholders));
                }
                
                // 完成后关闭界面
                if (closeAfterComplete) {
                    org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        player.closeInventory();
                    }, 5L);
                }
            }, 1L);
        } else {
            player.sendMessage(configManager.getMessage("error"));
        }
    }
}
