# BigExchange HTTP API 文档

## 概述

BigExchange 提供内置的 HTTP REST API，用于通过外部程序或脚本管理兑换码。所有管理接口均需要 API Key 认证。

- **基础地址**：`http://<服务器IP>:<端口>`
- **响应格式**：JSON
- **字符编码**：UTF-8

---

## 配置说明

在 `plugins/BigExchange/config.yml` 中配置：

```yaml
http-api:
  enabled: true          # 是否启用 HTTP API
  port: 11387            # 监听端口
  key: "your-secure-key" # API 认证密钥（必须修改！）
```

修改配置后重启服务器生效。

---

## 认证方式

所有管理接口（除健康检查外）必须携带以下任一请求头：

| 请求头 | 格式 |
|--------|------|
| `X-API-Key` | `your-secure-key` |
| `Authorization` | `Bearer your-secure-key` |

认证失败将返回 `401 Unauthorized`：

```json
{
  "success": false,
  "error": "Unauthorized. Provide X-API-Key header or Authorization: Bearer <key>."
}
```

---

## 接口列表

### 1. 健康检查

无需认证，用于检测服务状态。

- **URL**：`GET /api/health`
- **响应**：

```json
{
  "status": "ok",
  "plugin": "BigExchange",
  "version": "1.5"
}
```

---

### 2. 查看所有兑换码

- **URL**：`GET /api/codes`
- **响应**：

```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "code": "AbC123-XyZ7890123",
      "uses": 5,
      "usedCount": 2,
      "playerUses": -1,
      "createdBy": "admin",
      "createdTime": "2026-06-30 12:00:00.0",
      "isActive": true,
      "lastUsed": "2026-06-30 14:30:00.0",
      "rewardCommands": "give {player} diamond 1",
      "expirationTime": "2026-07-30 12:00:00.0",
      "validityDays": 30,
      "remainingUses": 3,
      "isExpired": false,
      "formattedRemainingTime": "29天23小时"
    }
  ]
}
```

---

### 3. 查看单个兑换码

- **URL**：`GET /api/codes/{id}`
- **路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| `id` | integer | 兑换码数据库 ID |

- **成功响应**：

```json
{
  "success": true,
  "data": {
    "id": 1,
    "code": "AbC123-XyZ7890123",
    "uses": 5,
    "usedCount": 2,
    "playerUses": -1,
    "createdBy": "admin",
    "createdTime": "2026-06-30 12:00:00.0",
    "isActive": true,
    "lastUsed": "2026-06-30 14:30:00.0",
    "rewardCommands": "give {player} diamond 1",
    "expirationTime": "2026-07-30 12:00:00.0",
    "validityDays": 30,
    "remainingUses": 3,
    "isExpired": false,
    "formattedRemainingTime": "29天23小时"
  }
}
```

- **失败响应**（`404`）：

```json
{
  "success": false,
  "error": "Code not found"
}
```

---

### 4. 生成兑换码

- **URL**：`POST /api/codes`
- **请求体**（JSON）：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `uses` | integer | 否 | `-1` | 总使用次数，`-1` 表示无限 |
| `playerUses` | integer | 否 | `-1` | 单个玩家使用次数，`-1` 表示无限制 |
| `validityDays` | integer | 否 | `30` | 有效期天数，`-1` 表示永久 |
| `rewardCommands` | string | 否 | `null` | 奖励命令，多条用 `;` 分隔，支持 `{player}` 占位符 |
| `createdBy` | string | 否 | `"HTTP_API"` | 创建者标识 |

- **请求示例**：

```json
{
  "uses": 10,
  "playerUses": 1,
  "validityDays": 7,
  "rewardCommands": "give {player} diamond 1;eco give {player} 100",
  "createdBy": "web_admin"
}
```

- **成功响应**（`201`）：

```json
{
  "success": true,
  "message": "Code created",
  "data": {
    "id": 2,
    "code": "aB3dE5-fGhIjKlMnOp",
    "uses": 10,
    "usedCount": 0,
    ...
  }
}
```

---

### 5. 编辑兑换码

- **URL**：`PUT /api/codes/{id}`
- **路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| `id` | integer | 兑换码数据库 ID |

- **请求体**（JSON，按需传入需要修改的字段）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `uses` | integer | 新的总使用次数，`-1` 表示无限 |
| `validityDays` | integer | 新的有效期天数，`-1` 表示永久 |
| `rewardCommands` | string | 新的奖励命令（覆盖原有） |
| `active` | boolean | `true` 激活，`false` 停用 |

- **请求示例**：

```json
{
  "uses": 20,
  "validityDays": -1,
  "active": true
}
```

- **成功响应**：

```json
{
  "success": true,
  "message": "Code updated",
  "data": {
    "id": 1,
    "uses": 20,
    "validityDays": -1,
    "isActive": true,
    ...
  }
}
```

- **失败响应**（`404`）：

```json
{
  "success": false,
  "error": "Code not found"
}
```

---

### 6. 删除兑换码

- **URL**：`DELETE /api/codes/{id}`
- **路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| `id` | integer | 兑换码数据库 ID |

- **成功响应**：

```json
{
  "success": true,
  "message": "Code deleted"
}
```

- **失败响应**（`404`）：

```json
{
  "success": false,
  "error": "Code not found"
}
```

---

## 通用状态码

| 状态码 | 含义 |
|--------|------|
| `200` | 请求成功 |
| `201` | 创建成功 |
| `400` | 请求参数错误（如 ID 格式无效） |
| `401` | 认证失败（未提供或提供了错误的 API Key） |
| `404` | 资源不存在（兑换码 ID 未找到） |
| `405` | 请求方法不允许 |
| `500` | 服务器内部错误 |

---

## 调用示例（curl）

```bash
# 1. 健康检查
curl http://localhost:11387/api/health

# 2. 查看所有兑换码
curl -H "X-API-Key: your-secure-key" \
  http://localhost:11387/api/codes

# 3. 查看单个兑换码
curl -H "X-API-Key: your-secure-key" \
  http://localhost:11387/api/codes/1

# 4. 生成兑换码
curl -X POST \
  -H "X-API-Key: your-secure-key" \
  -H "Content-Type: application/json" \
  -d '{"uses":5,"validityDays":30,"rewardCommands":"give {player} diamond 1"}' \
  http://localhost:11387/api/codes

# 5. 编辑兑换码
curl -X PUT \
  -H "X-API-Key: your-secure-key" \
  -H "Content-Type: application/json" \
  -d '{"uses":10,"active":true}' \
  http://localhost:11387/api/codes/1

# 6. 删除兑换码
curl -X DELETE \
  -H "X-API-Key: your-secure-key" \
  http://localhost:11387/api/codes/1
```

---

## 注意事项

1. **安全性**：生产环境务必修改默认 `key`，使用强随机字符串（建议 32 位以上）。
2. **网络访问**：HTTP API 监听所有网络接口 (`0.0.0.0`)，如需限制访问，请通过服务器防火墙或反向代理控制。
3. **端口冲突**：确保配置的端口未被其他程序占用。
