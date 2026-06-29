package top.mcocet.bigExchange.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.plugin.Plugin;
import top.mcocet.bigExchange.BigExchange;
import top.mcocet.bigExchange.manager.CodeManager;
import top.mcocet.bigExchange.manager.ConfigManager;
import top.mcocet.bigExchange.manager.DatabaseManager;
import top.mcocet.bigExchange.manager.LogManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * HTTP API 服务器
 * 提供兑换码的查看/编辑/生成/删除 REST 接口
 * 使用 X-API-Key 或 Authorization: Bearer 进行认证
 */
public class HttpApiServer {

    private final BigExchange plugin;
    private final ConfigManager configManager;
    private final LogManager logManager;
    private final ExchangeAPI exchangeAPI;
    private final CodeManager codeManager;
    private HttpServer server;
    private boolean running = false;

    public HttpApiServer(BigExchange plugin, ConfigManager configManager, ExchangeAPI exchangeAPI) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.exchangeAPI = exchangeAPI;
        this.codeManager = plugin.getCodeManager();
        this.logManager = plugin.getLogManager();
    }

    public void start() {
        if (!configManager.isHttpApiEnabled()) {
            logManager.info("HTTP API 已禁用");
            return;
        }

        int port = configManager.getHttpApiPort();
        String apiKey = configManager.getHttpApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            logManager.warning("HTTP API 已启用但未配置 API Key，请设置 http-api.key");
            return;
        }

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newFixedThreadPool(4));

            // 注册路由
            server.createContext("/api/codes", new CodesHandler());
            server.createContext("/api/codes/", new CodeDetailHandler());
            server.createContext("/api/health", new HealthHandler());

            server.start();
            running = true;
            logManager.info("HTTP API 已启动，监听端口：" + port);
        } catch (IOException e) {
            logManager.severe("HTTP API 启动失败：" + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            running = false;
            logManager.info("HTTP API 已停止");
        }
    }

    public boolean isRunning() {
        return running;
    }

    // ==================== 认证 ====================

    private boolean authenticate(HttpExchange exchange) {
        String apiKey = configManager.getHttpApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            return false;
        }

        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String providedKey = authHeader.substring(7).trim();
            if (providedKey.equals(apiKey)) {
                return true;
            }
        }

        String apiKeyHeader = exchange.getRequestHeaders().getFirst("X-API-Key");
        if (apiKeyHeader != null && apiKeyHeader.trim().equals(apiKey)) {
            return true;
        }

        return false;
    }

    private void sendAuthError(HttpExchange exchange) throws IOException {
        sendResponse(exchange, 401, "{\"success\":false,\"error\":\"Unauthorized. Provide X-API-Key header or Authorization: Bearer <key>.\"}");
    }

    // ==================== 通用响应 ====================

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        sendResponse(exchange, statusCode, json);
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ==================== JSON 工具 ====================

    private String escapeJson(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private String codeDataToJson(DatabaseManager.CodeData code) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":").append(code.id).append(",");
        sb.append("\"code\":").append(escapeJson(code.code)).append(",");
        sb.append("\"uses\":").append(code.uses).append(",");
        sb.append("\"usedCount\":").append(code.usedCount).append(",");
        sb.append("\"playerUses\":").append(code.playerUses).append(",");
        sb.append("\"createdBy\":").append(escapeJson(code.createdBy)).append(",");
        sb.append("\"createdTime\":").append(escapeJson(code.createdTime != null ? code.createdTime.toString() : null)).append(",");
        sb.append("\"isActive\":").append(code.isActive).append(",");
        sb.append("\"lastUsed\":").append(escapeJson(code.lastUsed != null ? code.lastUsed.toString() : null)).append(",");
        sb.append("\"rewardCommands\":").append(escapeJson(code.rewardCommands)).append(",");
        sb.append("\"expirationTime\":").append(escapeJson(code.expirationTime != null ? code.expirationTime.toString() : null)).append(",");
        sb.append("\"validityDays\":").append(code.validityDays).append(",");
        sb.append("\"remainingUses\":").append(code.getRemainingUses()).append(",");
        sb.append("\"isExpired\":").append(code.isExpired()).append(",");
        sb.append("\"formattedRemainingTime\":").append(escapeJson(code.getFormattedRemainingTime()));
        sb.append("}");
        return sb.toString();
    }

    // ==================== 处理器 ====================

    /**
     * /api/codes 路由处理器（列表 + 生成）
     */
    private class CodesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            try {
                if (!authenticate(exchange)) {
                    sendAuthError(exchange);
                    return;
                }

                if ("GET".equalsIgnoreCase(method)) {
                    handleList(exchange);
                } else if ("POST".equalsIgnoreCase(method)) {
                    handleCreate(exchange);
                } else {
                    sendResponse(exchange, 405, "{\"success\":false,\"error\":\"Method not allowed\"}");
                }
            } catch (Exception e) {
                logManager.warning("HTTP API 处理异常：" + e.getMessage());
                sendResponse(exchange, 500, "{\"success\":false,\"error\":\"Internal server error\"}");
            }
        }

        private void handleList(HttpExchange exchange) throws IOException {
            List<DatabaseManager.CodeData> codes = exchangeAPI.getAllCodes();
            StringBuilder sb = new StringBuilder();
            sb.append("{\"success\":true,\"data\":[");
            for (int i = 0; i < codes.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(codeDataToJson(codes.get(i)));
            }
            sb.append("]}");
            sendJson(exchange, 200, sb.toString());
        }

        private void handleCreate(HttpExchange exchange) throws IOException {
            String body = readBody(exchange);
            Map<String, String> params = parseJson(body);

            int uses = parseInt(params.get("uses"), -1);
            int playerUses = parseInt(params.get("playerUses"), -1);
            int validityDays = parseInt(params.get("validityDays"), configManager.getDefaultValidityDays());
            String rewardCommands = params.get("rewardCommands");
            String createdBy = params.getOrDefault("createdBy", "HTTP_API");

            String code = codeManager.generateCode(uses, playerUses, createdBy, rewardCommands, validityDays);
            DatabaseManager.CodeData codeData = exchangeAPI.getCodeByString(code);

            if (codeData != null) {
                sendJson(exchange, 201, "{\"success\":true,\"message\":\"Code created\",\"data\":" + codeDataToJson(codeData) + "}");
            } else {
                sendJson(exchange, 500, "{\"success\":false,\"error\":\"Failed to create code\"}");
            }
        }
    }

    /**
     * /api/codes/<id> 路由处理器（查看/编辑/删除）
     */
    private class CodeDetailHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            try {
                if (!authenticate(exchange)) {
                    sendAuthError(exchange);
                    return;
                }

                String path = exchange.getRequestURI().getPath();
                String idStr = path.substring("/api/codes/".length());
                int id;
                try {
                    id = Integer.parseInt(idStr);
                } catch (NumberFormatException e) {
                    sendResponse(exchange, 400, "{\"success\":false,\"error\":\"Invalid ID\"}");
                    return;
                }

                if ("GET".equalsIgnoreCase(method)) {
                    handleGet(exchange, id);
                } else if ("PUT".equalsIgnoreCase(method)) {
                    handleUpdate(exchange, id);
                } else if ("DELETE".equalsIgnoreCase(method)) {
                    handleDelete(exchange, id);
                } else {
                    sendResponse(exchange, 405, "{\"success\":false,\"error\":\"Method not allowed\"}");
                }
            } catch (Exception e) {
                logManager.warning("HTTP API 处理异常：" + e.getMessage());
                sendResponse(exchange, 500, "{\"success\":false,\"error\":\"Internal server error\"}");
            }
        }

        private void handleGet(HttpExchange exchange, int id) throws IOException {
            DatabaseManager.CodeData code = exchangeAPI.getCodeById(id);
            if (code == null) {
                sendResponse(exchange, 404, "{\"success\":false,\"error\":\"Code not found\"}");
                return;
            }
            sendJson(exchange, 200, "{\"success\":true,\"data\":" + codeDataToJson(code) + "}");
        }

        private void handleUpdate(HttpExchange exchange, int id) throws IOException {
            DatabaseManager.CodeData code = exchangeAPI.getCodeById(id);
            if (code == null) {
                sendResponse(exchange, 404, "{\"success\":false,\"error\":\"Code not found\"}");
                return;
            }

            String body = readBody(exchange);
            Map<String, String> params = parseJson(body);
            boolean updated = false;

            if (params.containsKey("uses")) {
                int newUses = parseInt(params.get("uses"), code.uses);
                updated |= exchangeAPI.modifyCodeUses(id, newUses);
            }
            if (params.containsKey("validityDays")) {
                int newValidity = parseInt(params.get("validityDays"), code.validityDays);
                updated |= exchangeAPI.modifyCodeValidity(id, newValidity);
            }
            if (params.containsKey("rewardCommands")) {
                updated |= codeManager.setRewardCommands(id, params.get("rewardCommands"));
            }
            if (params.containsKey("active")) {
                boolean active = Boolean.parseBoolean(params.get("active"));
                if (active) {
                    updated |= exchangeAPI.activateCode(id);
                } else {
                    updated |= exchangeAPI.deactivateCode(id);
                }
            }

            // 重新读取
            DatabaseManager.CodeData updatedCode = exchangeAPI.getCodeById(id);
            if (updatedCode != null) {
                sendJson(exchange, 200, "{\"success\":true,\"message\":\"Code updated\",\"data\":" + codeDataToJson(updatedCode) + "}");
            } else {
                sendJson(exchange, 500, "{\"success\":false,\"error\":\"Failed to update code\"}");
            }
        }

        private void handleDelete(HttpExchange exchange, int id) throws IOException {
            DatabaseManager.CodeData code = exchangeAPI.getCodeById(id);
            if (code == null) {
                sendResponse(exchange, 404, "{\"success\":false,\"error\":\"Code not found\"}");
                return;
            }

            boolean success = exchangeAPI.deleteCode(id);
            if (success) {
                sendJson(exchange, 200, "{\"success\":true,\"message\":\"Code deleted\"}");
            } else {
                sendJson(exchange, 500, "{\"success\":false,\"error\":\"Failed to delete code\"}");
            }
        }
    }

    /**
     * 健康检查
     */
    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            sendJson(exchange, 200, "{\"status\":\"ok\",\"plugin\":\"BigExchange\",\"version\":\"" + plugin.getDescription().getVersion() + "\"}");
        }
    }

    // ==================== 解析工具 ====================

    private Map<String, String> parseJson(String json) {
        Map<String, String> map = new java.util.HashMap<>();
        if (json == null || json.trim().isEmpty()) {
            return map;
        }
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return map;
        }
        trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        if (trimmed.isEmpty()) {
            return map;
        }

        // 简单解析 key:value 对（不支持嵌套）
        StringBuilder key = new StringBuilder();
        StringBuilder value = new StringBuilder();
        boolean inKey = true;
        boolean inString = false;
        boolean escape = false;

        for (char c : trimmed.toCharArray()) {
            if (escape) {
                if (inKey) key.append(c); else value.append(c);
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                if (inKey) key.append(c); else value.append(c);
                continue;
            }
            if (c == '"') {
                inString = !inString;
                if (inKey) key.append(c); else value.append(c);
                continue;
            }
            if (!inString && c == ':') {
                inKey = false;
                continue;
            }
            if (!inString && c == ',') {
                map.put(unquote(key.toString().trim()), unquote(value.toString().trim()));
                key.setLength(0);
                value.setLength(0);
                inKey = true;
                continue;
            }
            if (inKey) key.append(c); else value.append(c);
        }
        if (key.length() > 0) {
            map.put(unquote(key.toString().trim()), unquote(value.toString().trim()));
        }
        return map;
    }

    private String unquote(String s) {
        if (s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private int parseInt(String s, int defaultValue) {
        if (s == null || s.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
