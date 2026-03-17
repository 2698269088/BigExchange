package top.mcocet.bigExchange;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import top.mcocet.bigExchange.api.ExchangeAPI;
import top.mcocet.bigExchange.command.CommandHandler;
import top.mcocet.bigExchange.command.Craftconomy3QueryCommand;
import top.mcocet.bigExchange.command.PurchaseCommand;
import top.mcocet.bigExchange.command.RedeemCommand;
import top.mcocet.bigExchange.listener.ChatInputListener;
import top.mcocet.bigExchange.listener.FormListener;
import top.mcocet.bigExchange.manager.CodeExpirationTask;
import top.mcocet.bigExchange.manager.CodeManager;
import top.mcocet.bigExchange.manager.ConfigManager;
import top.mcocet.bigExchange.manager.DatabaseManager;
import top.mcocet.bigExchange.manager.LogManager;

public final class BigExchange extends JavaPlugin {

    private static BigExchange instance;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private CodeManager codeManager;
    private ExchangeAPI exchangeAPI;
    private ChatInputListener chatInputListener;
    private FormListener formListener;
    private LogManager logManager;
    private CodeExpirationTask expirationTask;
    private top.mcocet.bigExchange.manager.craftconomy3.Craftconomy3MySQLHandler craftconomy3Handler;

    @Override
    public void onEnable() {
        instance = this;
        
        // 初始化配置管理器
        configManager = new ConfigManager(this);
        
        // 初始化日志管理器
        logManager = new LogManager(this, configManager);
        logManager.info("配置已加载");
        
        // 初始化数据库
        if (configManager.isDatabaseEnabled()) {
            databaseManager = new DatabaseManager(this, configManager);
            logManager.info("数据库已连接");
        } else {
            logManager.severe("数据库未启用，插件无法正常工作");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        
        // 初始化兑换码管理器
        codeManager = new CodeManager(this, databaseManager, configManager);
        logManager.info("兑换码管理器已初始化");
        
        // 注册监听器（必须在注册命令之前，因为 RedeemCommand 需要 FormListener）
        registerListeners();
        logManager.info("监听器已注册");
        
        // 注册命令
        registerCommands();
        logManager.info("命令已注册");
        
        // 初始化 API
        exchangeAPI = new ExchangeAPI(codeManager, databaseManager);
        logManager.info("API 已就绪");
        
        // 启动过期检查任务
        int checkInterval = configManager.getAutoCheckInterval();
        if (checkInterval > 0) {
            expirationTask = new CodeExpirationTask(this, databaseManager);
            expirationTask.start(checkInterval);
            logManager.info("自动过期检查已启用（间隔：" + checkInterval + "分钟）");
        } else {
            logManager.info("自动过期检查已禁用");
        }
        
        // 初始化 Craftconomy3 数据库处理器（如果启用）
        if (configManager.isCraftconomy3Enabled()) {
            craftconomy3Handler = new top.mcocet.bigExchange.manager.craftconomy3.Craftconomy3MySQLHandler(
                this,
                configManager.getCraftconomy3MysqlHost(),
                configManager.getCraftconomy3MysqlPort(),
                configManager.getCraftconomy3MysqlDatabase(),
                configManager.getCraftconomy3MysqlUsername(),
                configManager.getCraftconomy3MysqlPassword(),
                configManager.getCraftconomy3MysqlTablePrefix(),
                configManager.getCraftconomy3CurrencyName(),
                configManager.getCraftconomy3WorldGroup()
            );
            if (craftconomy3Handler.isConnected()) {
                logManager.info("Craftconomy3 数据库已连接（用于购买扣款）");
            } else {
                logManager.severe("Craftconomy3 数据库连接失败，购买功能可能无法正常工作");
            }
        } else {
            logManager.info("Craftconomy3 扣款功能未启用");
        }
        
        logManager.info("插件已启用！");
    }

    @Override
    public void onDisable() {
        // 停止过期检查任务
        if (expirationTask != null) {
            expirationTask.stop();
        }
        
        // 关闭 Craftconomy3 数据库连接
        if (craftconomy3Handler != null) {
            craftconomy3Handler.close();
            logManager.info("Craftconomy3 数据库连接已关闭");
        }
        
        // 关闭数据库连接
        if (databaseManager != null) {
            databaseManager.close();
            logManager.info("数据库已关闭");
        }
        
        logManager.info("插件已禁用！");
    }

    private void registerCommands() {
        // 注册管理员命令
        CommandHandler commandHandler = new CommandHandler(this, codeManager, databaseManager, configManager);
        getCommand("bigexchange").setExecutor(commandHandler);
        getCommand("bigexchange").setTabCompleter(commandHandler);
        
        // 注册兑换命令
        getCommand("redeem").setExecutor(new RedeemCommand(codeManager, configManager, formListener));
        
        // 注册购买命令
        if (configManager.isPurchaseEnabled()) {
            getCommand("purchase").setExecutor(new PurchaseCommand(this, codeManager, configManager));
            logManager.info("购买命令已注册 (/purchase)");
        }
        
        // 注册 Craftconomy3 查询命令
        if (configManager.isCraftconomy3Enabled()) {
            Craftconomy3QueryCommand queryCommand = new Craftconomy3QueryCommand(this, configManager);
            getCommand("c3query").setExecutor(queryCommand);
            getCommand("c3query").setTabCompleter(queryCommand);
            logManager.info("Craftconomy3 查询命令已注册 (/c3query)");
        }
    }

    private void registerListeners() {
        // 注册聊天输入监听器
        chatInputListener = new ChatInputListener(this, codeManager, configManager);
        Bukkit.getPluginManager().registerEvents(chatInputListener, this);
        
        // 注册表单监听器
        FormListener formListener = new FormListener(this, codeManager, configManager);
        Bukkit.getPluginManager().registerEvents(formListener, this);
        
        // 保存 formListener 引用供命令使用
        this.formListener = formListener;
    }

    /**
     * 获取插件实例
     * @return 插件实例
     */
    public static BigExchange getInstance() {
        return instance;
    }

    /**
     * 获取配置管理器
     * @return 配置管理器
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * 获取数据库管理器
     * @return 数据库管理器
     */
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    /**
     * 获取兑换码管理器
     * @return 兑换码管理器
     */
    public CodeManager getCodeManager() {
        return codeManager;
    }

    /**
     * 获取日志管理器
     * @return 日志管理器
     */
    public LogManager getLogManager() {
        return logManager;
    }

    /**
     * 获取公共 API
     * @return 公共 API
     */
    public ExchangeAPI getExchangeAPI() {
        return exchangeAPI;
    }

    /**
     * 获取聊天输入监听器
     * @return 聊天输入监听器
     */
    public ChatInputListener getChatInputListener() {
        return chatInputListener;
    }

    /**
     * 获取 Craftconomy3 数据库处理器
     * @return Craftconomy3 处理器
     */
    public top.mcocet.bigExchange.manager.craftconomy3.Craftconomy3MySQLHandler getCraftconomy3Handler() {
        return craftconomy3Handler;
    }
}
