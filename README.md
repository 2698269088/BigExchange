# BigExchange - Minecraft 兑换码插件

一个功能强大的 Minecraft 兑换码生成和管理插件，支持 Java 版和国际基岩版。

## ✨ 功能特性

### 🎯 核心功能

- **随机兑换码生成**：支持自定义长度和格式（默认：XXXXXX-XXXXXXXXXX）
- **使用次数管理**：可设置有限次数或无限次使用
- **奖励命令系统**：兑换后自动执行预设命令
- **SHA-256 加密**：防破解认证机制
- **双数据库支持**：SQLite（默认）和 MySQL，支持双备份
- **多平台支持**：Java 版（聊天栏）、国际基岩版（表单 UI）

### 🌍 跨平台支持

| 平台 | 兑换方式 | 所需插件               |
|------|---------|--------------------|
| Java 版 | 聊天栏输入 | 无                  |
| 国际基岩版 | 表单 UI | Geyser + Floodgate |

### 📊 数据库特性

- **SQLite**：轻量级，无需额外配置
- **MySQL**：高性能，支持连接池（HikariCP）
- **双备份模式**：同时写入两个数据库，数据安全

### 🔧 调试特性

- **灵活的日志级别**：支持 INFO, WARNING, SEVERE, FINE, FINER, FINEST
- **详细的调试日志**：帮助排查表单和数据库问题

---

## 📦 安装说明

### 前置要求

- Minecraft Server 1.18.2+
- Java 17+
- Maven（编译需要）

### 安装步骤

1. 下载编译好的 `BigExchange.jar`
2. 将文件放入服务器 `plugins/` 目录
3. 重启服务器
4. 编辑 `plugins/BigExchange/config.yml` 配置文件

### 依赖插件（可选）

- **Geyser-Spigot**：基岩版玩家接入
- **Floodgate**：基岩版玩家身份验证

> **注意**：本插件仅支持国际版基岩版玩家（通过 Geyser + Floodgate），不支持网易版。

---

## ⚙️ 配置文件详解

### config.yml

```yaml
# ============================================
# 数据库配置
# ============================================
database:
  # 是否启用数据库（必须为 true）
  enabled: true
  
  # 数据库类型：sqlite 或 mysql
  type: "sqlite"
  
  # SQLite 数据库文件路径
  path: "plugins/BigExchange/codes.db"
  
  # MySQL 配置（仅在 type 为 mysql 时使用）
  mysql:
    host: "localhost"
    port: 3306
    database: "bigexchange"
    username: "root"
    password: "password"
    
    # 连接池配置
    pool:
      min-size: 5
      max-size: 20
      max-lifetime: 1800000
    
    # 表前缀
    table-prefix: "be_"
  
  # 备份配置
  backup:
    # 是否启用双数据库备份
    dual-backup: false
    # 自动备份间隔（分钟，0 表示不自动备份）
    auto-backup-interval: 60

# ============================================
# 兑换码格式配置
# ============================================
code:
  format:
    # 第一部分长度（分隔符前）
    first-length: 6
    # 第二部分长度（分隔符后）
    second-length: 10
    # 分隔符
    separator: "-"
  
  # 安全配置
  security:
    # 是否启用加密哈希
    encrypt: true
    # 哈希算法
    algorithm: "SHA-256"

# ============================================
# 日志配置
# ============================================
logging:
  # 日志级别：INFO, WARNING, SEVERE, FINE, FINER, FINEST
  # INFO: 普通信息（默认）
  # WARNING: 警告信息
  # SEVERE: 严重错误
  # FINE: 调试信息（推荐用于排查问题）
  # FINER: 详细调试信息（包含所有 FINE 信息）
  # FINEST: 最详细调试信息（包含所有 FINER 信息）
  
  # 如果基岩版玩家无法打开表单，建议设置为 FINE 或 FINER 查看详细日志
  level: "INFO"

# ============================================
# 基岩版表单配置
# ============================================
form:
  # 是否启用基岩版表单 UI
  enabled: true
  
  # 表单类型：geyser (国际版 Geyser/Floodgate)
  # 注意：已移除网易版支持，仅保留国际版
  type: "geyser"
  
  # 表单标题
  redeem-title: "&6 兑换码兑换"
  
  # 表单内容
  redeem-content: "&e 请输入您的兑换码：\n&7 格式：XXXXXX-XXXXXXXXXX"
  
  # 输入框占位符
  redeem-placeholder: "XXXXXX-XXXXXXXXXX"
  
  # 成功按钮
  redeem-button-confirm: "&a 确认兑换"
  
  # 取消按钮
  redeem-button-cancel: "&c 取消"
  
  # 玩家类型检测模式
  # auto: 自动检测（推荐）
  # force-form: 强制对所有玩家显示表单
  # force-chat: 强制使用聊天栏输入
  detection-mode: "auto"

# ============================================
# 消息配置
# ============================================
messages:
  # 消息前缀
  prefix: "&8[&6BigExchange&8] "
  
  # 成功消息
  success: "&a 操作成功！"
  
  # 错误消息
  error: "&c 操作失败！"
  
  # 兑换码生成
  code-generated: "&7 已生成兑换码：&b%code% &7，可用次数：%uses%"
  
  # 兑换成功
  code-redeemed: "&a 兑换成功！获得奖励"
  
  # 无效兑换码
  code-invalid: "&c 无效的兑换码"
  
  # 过期兑换码
  code-expired: "&c 兑换码已失效"
  
  # 无剩余次数
  code-no-uses: "&c 该兑换码已无可用次数"
  
  # 管理员强制激活
  admin-force-activated: "&a 强制激活兑换码成功"
  
  # 达到使用限制（详细提示）
  code-limit-reached: "&c 该兑换码已达到最大使用限制（%used%/%total% 次）"
  
  # 剩余次数提示
  code-remaining-uses: "&7 该兑换码剩余可用次数：&e%remaining%&7/&e%total%"
```

---

## 🎮 玩家命令

### `/redeem` - 兑换兑换码

**用法 1：打开兑换界面**
```
/redeem
```
- Java 版玩家：弹出铁砧GUI（AnvilGUI）
- 国际基岩版玩家：弹出表单 UI（如果启用且安装了 Geyser+Floodgate）

**用法 2：直接输入兑换码**
```
/redeem ABCDEF-1234567890
```
- 立即兑换指定兑换码

---

## 🔧 管理员命令

### `/be` 或 `/bigexchange` - 插件管理

#### 生成兑换码
```
/be generate <次数|unlimited> [奖励命令]
```
**示例：**
```bash
# 生成可使用 5 次的兑换码
/be generate 5

# 生成无限次兑换码
/be generate unlimited

# 生成带奖励命令的兑换码（多个命令用分号分隔）
/be generate 5 give {player} diamond 1;give {player} iron_ingot 10

# 使用占位符 {player} 会被替换为玩家名字
/be generate 3 eco give {player} 1000
```

#### 删除兑换码
```
/be delete <兑换码|ID>
```
**示例：**
```bash
/be delete ABCDEF-1234567890
/be delete 123  # 通过数据库 ID 删除
```

#### 修改使用次数
```
/be modify <兑换码|ID> <次数|unlimited>
```
**示例：**
```bash
/be modify ABCDEF-1234567890 10
/be modify 123 unlimited
```

#### 列出兑换码
```
/be list [all|active|used|expired]
```
**示例：**
```bash
/be list        # 列出所有兑换码
/be list all    # 列出所有
/be list active # 只列出有效的
/be list used   # 列出已使用的
/be list expired # 列出已过期的
```

#### 查看兑换码信息
```
/be info <兑换码|ID>
```
**显示内容：**
- 兑换码
- 使用次数/总次数
- 创建者
- 创建时间
- 是否激活
- 奖励命令

#### 查看使用历史
```
/be history <兑换码|ID>
```
**显示内容：**
- 使用玩家
- 使用时间
- 使用记录列表

#### 强制激活兑换码
```
/be activate <兑换码|ID>
```
- 将已过期的兑换码重新激活

#### 清空数据
```
/be clear <all|codes|history>
```
**示例：**
```bash
/be clear all      # 清空所有数据
/be clear codes    # 只清空兑换码
/be clear history  # 只清空使用历史
```

#### 备份数据库
```
/be backup <sqlite|mysql|both>
```
**示例：**
```bash
/be backup sqlite  # 备份 SQLite 数据库
/be backup mysql   # MySQL 数据备份到 SQLite
/be backup both    # 同时备份
```

#### 重载配置
```
/be reload
```
- 重新加载配置文件

---

## 🔌 API 使用（开发者）

### 获取 API 实例

```java
ExchangeAPI api = BigExchange.getInstance().getExchangeAPI();
```

### 可用方法

```java
// 生成兑换码
String code = api.generateCode(int uses, String createdBy, String rewardCommands);

// 验证兑换码
boolean isValid = api.verifyCode(String code);

// 使用兑换码
boolean success = api.useCode(String code, UUID playerUuid, String playerName);

// 获取兑换码信息
CodeData codeData = api.getCodeData(String code);

// 修改使用次数
boolean success = api.modifyCodeUses(int codeId, int newUses);

// 删除兑换码
boolean success = api.deleteCode(int codeId);

// 获取剩余次数
int remaining = api.getRemainingUses(int codeId);
```

### 使用示例

```java
public class MyPlugin {
    private BigExchange bigExchange;
    private ExchangeAPI exchangeAPI;
    
    public void onEnable() {
        // 获取 BigExchange API
        Plugin plugin = Bukkit.getPluginManager().getPlugin("BigExchange");
        if (plugin instanceof BigExchange) {
            bigExchange = (BigExchange) plugin;
            exchangeAPI = bigExchange.getExchangeAPI();
            
            // 生成一个兑换码
            String code = exchangeAPI.generateCode(5, "System", "give {player} diamond 1");
            getLogger().info("生成的兑换码：" + code);
        }
    }
    
    // 在其他插件中使用兑换码
    public void redeemCode(Player player, String code) {
        if (exchangeAPI != null) {
            boolean success = exchangeAPI.useCode(code, player.getUniqueId(), player.getName());
            if (success) {
                player.sendMessage("§a兑换成功！");
            } else {
                player.sendMessage("§c兑换失败！");
            }
        }
    }
}
```

---

## 🎯 奖励命令系统

### 基本语法

在生成兑换码时指定奖励命令，多个命令用分号 `;` 分隔：

```bash
/be generate 5 "命令 1;命令 2;命令 3"
```

### 占位符

- `{player}` - 兑换玩家的名称

### 使用示例

```bash
# 给予物品
/be generate 5 "give {player} diamond 10"

# 多个奖励
/be generate 3 "give {player} diamond_sword;give {player} iron_armor 1"

# 经济奖励（需要经济插件）
/be generate 10 "eco give {player} 1000"

# 权限组（需要权限插件）
/be generate 1 "lp user {player} parent add vip"

# 广播通知
/be generate 1 "broadcast {player} 抽中了特等奖！"

# 组合命令
/be generate 5 "give {player} diamond 5;eco give {player} 500;broadcast {player} 获得了丰厚奖励！"
```

---

## 📊 数据库结构

### exchange_codes 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT | 主键 |
| code | VARCHAR(32) | 兑换码明文 |
| code_hash | VARCHAR(64) | SHA-256 哈希 |
| uses | INT | 使用次数（-1=无限） |
| used_count | INT | 已使用次数 |
| created_by | VARCHAR(50) | 创建者 |
| created_time | TIMESTAMP | 创建时间 |
| is_active | BOOLEAN | 是否激活 |
| last_used | TIMESTAMP | 最后使用时间 |
| reward_commands | TEXT | 奖励命令（分号分隔） |
| metadata | TEXT | 元数据（JSON） |

### code_usage 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT | 主键 |
| code_id | INT | 兑换码 ID（外键） |
| player_uuid | VARCHAR(36) | 玩家 UUID |
| player_name | VARCHAR(50) | 玩家名称 |
| used_time | TIMESTAMP | 使用时间 |

---

## 🔒 安全特性

### SHA-256 加密
- 所有兑换码都生成哈希值存储在数据库中
- 验证时同时检查明文和哈希值
- 防止伪造和破解

### 防刷机制
- 每个玩家只能使用一次同一个兑换码
- 使用次数用完后自动停用
- 数据库事务保证数据一致性

---

## ⚠️ 常见问题

### Q: 国际基岩版玩家看不到表单？
**A:** 检查以下配置：
1. 确认安装了 Geyser 和 Floodgate
2. 检查 `form.enabled: true`
3. 确认玩家是基岩版登录（通过 Floodgate 认证）
4. 将日志级别设置为 FINE 查看详细调试信息

### Q: 如何查看详细的表单调试日志？
**A:** 将配置文件中的日志级别设置为 `FINE` 或 `FINER`：
```yaml
logging:
  level: "FINE"   # 或 "FINER" 查看详细调试信息
```
重启插件后，控制台会显示详细的表单检测和发送过程日志。

### Q: 使用 JavaGUI 时报错？
**A:** 这是 Spartan 反作弊插件与 AnvilGUI 的兼容性问题，不影响功能使用。
1. **忽略该错误** - 功能完全正常，只是控制台会有报错
2. **调整 Spartan 配置** - 在 Spartan 配置中过滤相关错误
3. **暂时禁用 AnvilGUI** - 在 config.yml 中设置 `anvil-gui-enabled: false`

### Q: 奖励命令不执行？
**A:** 检查：
1. 命令格式是否正确（分号分隔）
2. 占位符 `{player}` 是否正确
3. 服务器是否有执行该命令的权限
4. 查看控制台是否有错误日志

### Q: 如何备份数据？
**A:** 使用命令：
```bash
/be backup sqlite  # 备份 SQLite
/be backup both    # 双备份
```
备份文件位置：`plugins/BigExchange/backups/`

### Q: MySQL 连接失败？
**A:** 检查：
1. MySQL 服务是否运行
2. 数据库是否已创建
3. 用户名密码是否正确
4. 防火墙是否开放 3306 端口

---

## 📝 更新日志

### v1.2
- ✅ 为 Java版玩家添加 AnvilGUI 铁砧界面
- ✅ 智能判断玩家类型（Java版/基岩版）使用不同界面
- ✅ 优化点击处理，防止快速点击导致的报错
- ✅ 兑换完成后自动关闭界面
- ✅ 移除网易版支持，专注于国际版
- ✅ 精简代码，提高性能
- ✅ 优化表单处理逻辑
- ✅ 改进调试日志输出

### v1.0
- ✅ 初始版本发布
- ✅ 兑换码生成和管理
- ✅ SQLite/MySQL 支持
- ✅ 国际基岩版表单支持
- ✅ 奖励命令系统
- ✅ 数据库清空和备份
- ✅ 详细的使用提示

---

## 📄 开源协议

MIT License

---

## 💖 致谢

感谢以下开源项目：

- **SpigotMC** - Minecraft 服务器框架
- **GeyserMC** - 基岩版接入协议
- **Floodgate** - 基岩版身份验证
- **Cumulus** - 表单 UI 库
- **HikariCP** - MySQL 连接池

---

**Made with ❤️ for Minecraft Community**
