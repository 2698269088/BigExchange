package top.mcocet.bigExchange.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Folia 兼容的调度器工具类
 * 自动检测是否在 Folia/Luminol 环境下运行，并使用相应的调度 API
 * 
 * 注意：Luminol 等 Folia fork 可能不完全支持 Bukkit Scheduler，
 * 因此在 Folia 环境下必须使用 Folia 的调度器 API
 */
public class FoliaScheduler {
    
    private static final boolean IS_FOLIA;
    private static Class<?> globalRegionSchedulerClass;
    private static Method globalRunMethod;
    private static Method globalRunLaterMethod;
    private static Method globalRunRepeatingMethod;
    private static Logger logger;
    
    static {
        // 检测是否为 Folia 环境
        boolean isFolia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
            
            // 初始化 Folia 调度器类和方法
            initializeFoliaSchedulers();
        } catch (ClassNotFoundException e) {
            // 不是 Folia 环境
        }
        IS_FOLIA = isFolia;
    }
    
    /**
     * 设置 Logger（由插件调用）
     */
    public static void setLogger(Logger pluginLogger) {
        logger = pluginLogger;
    }
    
    private static void logInfo(String message) {
        if (logger != null) {
            logger.info(message);
        }
    }
    
    private static void logWarning(String message) {
        if (logger != null) {
            logger.warning(message);
        }
    }
    
    private static void logSevere(String message) {
        if (logger != null) {
            logger.severe(message);
        }
    }
    
    /**
     * 初始化 Folia 调度器相关反射
     */
    private static void initializeFoliaSchedulers() {
        try {
            // GlobalRegionScheduler - 全局区域调度器（优先使用）
            globalRegionSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            
            // 获取方法引用 - 使用正确的参数类型
            // run(Plugin, Consumer<ScheduledTask>)
            globalRunMethod = globalRegionSchedulerClass.getMethod("run", Plugin.class, java.util.function.Consumer.class);
            // runDelayed(Plugin, Consumer<ScheduledTask>, long)
            globalRunLaterMethod = globalRegionSchedulerClass.getMethod("runDelayed", Plugin.class, java.util.function.Consumer.class, long.class);
            // runAtFixedRate(Plugin, Consumer<ScheduledTask>, long, long)
            globalRunRepeatingMethod = globalRegionSchedulerClass.getMethod("runAtFixedRate", Plugin.class, java.util.function.Consumer.class, long.class, long.class);
            
            logInfo("Folia 全局调度器初始化成功");
        } catch (Exception e) {
            logWarning("Folia 调度器初始化失败: " + e.getMessage());
            // 不打印堆栈，避免日志污染
        }
    }
    
    /**
     * 判断当前是否为 Folia 环境
     */
    public static boolean isFolia() {
        return IS_FOLIA;
    }
    
    /**
     * 同步执行任务（主线程）
     * 在 Folia 中使用全局区域调度器
     */
    public static void runSync(Plugin plugin, Runnable task) {
        if (IS_FOLIA && globalRunMethod != null) {
            try {
                Object scheduler = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer());
                // Consumer<ScheduledTask> - 忽略 ScheduledTask 参数
                globalRunMethod.invoke(scheduler, plugin, (java.util.function.Consumer<?>) scheduledTask -> task.run());
                return;
            } catch (Exception e) {
                // 静默失败，降级到 Bukkit Scheduler
            }
        }
        // 降级：使用 Bukkit Scheduler
        Bukkit.getScheduler().runTask(plugin, task);
    }
    
    /**
     * 延迟执行任务
     * @param delayTicks 延迟 tick 数
     */
    public static void runSyncLater(Plugin plugin, Runnable task, long delayTicks) {
        if (IS_FOLIA && globalRunLaterMethod != null) {
            try {
                Object scheduler = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer());
                globalRunLaterMethod.invoke(scheduler, plugin, (java.util.function.Consumer<?>) scheduledTask -> task.run(), delayTicks);
                return;
            } catch (Exception e) {
                // 静默失败，降级到 Bukkit Scheduler
            }
        }
        // 降级：使用 Bukkit Scheduler
        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }
    
    /**
     * 重复执行任务
     * @param delayTicks 初始延迟 tick 数
     * @param periodTicks 重复间隔 tick 数
     */
    public static org.bukkit.scheduler.BukkitTask runSyncRepeating(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (IS_FOLIA && globalRunRepeatingMethod != null) {
            try {
                Object scheduler = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer());
                globalRunRepeatingMethod.invoke(scheduler, plugin, (java.util.function.Consumer<?>) scheduledTask -> task.run(), delayTicks, periodTicks);
                return null; // Folia 不返回 BukkitTask
            } catch (Exception e) {
                logSevere("Folia 重复任务调度失败: " + e.getMessage());
                throw new RuntimeException("无法在 Folia 环境下调度重复任务", e);
            }
        }
        // 非 Folia 环境：使用 Bukkit Scheduler
        return Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
    }
    
    /**
     * 异步执行任务
     */
    public static void runAsync(Plugin plugin, Runnable task) {
        if (IS_FOLIA) {
            try {
                Object scheduler = Bukkit.getServer().getClass().getMethod("getAsyncScheduler").invoke(Bukkit.getServer());
                java.lang.reflect.Method asyncRunMethod = scheduler.getClass().getMethod("runNow", Plugin.class, java.util.function.Consumer.class);
                asyncRunMethod.invoke(scheduler, plugin, (java.util.function.Consumer<?>) scheduledTask -> task.run());
                return;
            } catch (Exception e) {
                // 静默失败，降级到 Bukkit Scheduler
            }
        }
        // 降级：使用 Bukkit Scheduler
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }
    
    /**
     * 延迟异步执行任务
     * @param delay 延迟时间
     * @param unit 时间单位
     */
    public static void runAsyncLater(Plugin plugin, Runnable task, long delay, java.util.concurrent.TimeUnit unit) {
        if (IS_FOLIA) {
            try {
                Object scheduler = Bukkit.getServer().getClass().getMethod("getAsyncScheduler").invoke(Bukkit.getServer());
                java.lang.reflect.Method asyncRunDelayedMethod = scheduler.getClass().getMethod("runDelayed", Plugin.class, java.util.function.Consumer.class, long.class, java.util.concurrent.TimeUnit.class);
                asyncRunDelayedMethod.invoke(scheduler, plugin, (java.util.function.Consumer<?>) scheduledTask -> task.run(), delay, unit);
                return;
            } catch (Exception e) {
                // 静默失败，降级到 Bukkit Scheduler
            }
        }
        // 降级：使用 Bukkit Scheduler
        long delayTicks = unit.toMillis(delay) / 50; // 转换为 tick
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
    }
}
