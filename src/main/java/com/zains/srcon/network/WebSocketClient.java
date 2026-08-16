package com.zains.srcon.network;

import com.zains.srcon.SRConMod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket 客户端：使用 JDK 内置 java.net.http.WebSocket
 * 连接 AstrBot 插件的 WebSocket 服务端
 */
public class WebSocketClient {
    private final String wsUrl;
    private final String serverId;
    private final String serverName;
    private final String token;
    private WebSocket ws;
    private volatile boolean connected = false;
    private volatile boolean closed = false;
    private Thread reconnectThread = null;

    public WebSocketClient(String wsUrl, String serverId, String serverName, String token) {
        this.wsUrl = wsUrl;
        this.serverId = serverId;
        this.serverName = serverName;
        this.token = token;
    }

    public void connect() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .build();
            ws = client.newWebSocketBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .buildAsync(URI.create(wsUrl), new Listener())
                    .get(15, TimeUnit.SECONDS);
            // 握手成功回调 onOpen 在 Listener 里处理
        } catch (Exception e) {
            SRConMod.LOGGER.warn("[SRCon] WebSocket 连接失败: {}", e.getMessage());
            connected = false;
            scheduleReconnect();
        }
    }

    public void sendMessage(String message) {
        if (ws != null && connected) {
            ws.sendText(message, true);
        }
    }

    public void sendChat(String player, String message) {
        sendJson("chat", "{\"player\":\"" + escapeJson(player) + "\",\"msg\":\"" + escapeJson(message) + "\"}");
    }

    public void sendJoin(String player) {
        sendJson("join", "{\"player\":\"" + escapeJson(player) + "\"}");
    }

    public void sendLeave(String player) {
        sendJson("leave", "{\"player\":\"" + escapeJson(player) + "\"}");
    }

    public void sendDeath(String player, String reason) {
        sendJson("death", "{\"player\":\"" + escapeJson(player) + "\",\"reason\":\"" + escapeJson(reason) + "\"}");
    }

    public void sendAchievement(String player, String title) {
        sendJson("achievement", "{\"player\":\"" + escapeJson(player) + "\",\"title\":\"" + escapeJson(title) + "\"}");
    }

    public void sendServerStart() {
        sendJson("server_start", "{\"server_name\":\"" + escapeJson(serverName) + "\"}");
    }

    private void sendJson(String type, String dataJson) {
        String inner = dataJson.trim();
        if (inner.startsWith("{")) inner = inner.substring(1);
        if (inner.endsWith("}")) inner = inner.substring(0, inner.length() - 1);
        String json = "{\"type\":\"" + type + "\",\"server\":\"" + escapeJson(serverId) + "\",\"server_name\":\"" + escapeJson(serverName) + "\"," + inner + "}";
        sendMessage(json);
    }

    public boolean isConnected() { return connected; }

    public void close() {
        closed = true;
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "server stopping");
        }
    }

    private void scheduleReconnect() {
        if (closed) return;
        if (reconnectThread != null && reconnectThread.isAlive()) return;
        reconnectThread = new Thread(() -> {
            try {
                Thread.sleep(10000);
                SRConMod.LOGGER.info("[SRCon] 尝试重连 WebSocket...");
                connect();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "srcon-reconnect");
        reconnectThread.setDaemon(true);
        reconnectThread.start();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 消息监听器 */
    private class Listener implements WebSocket.Listener {
        private StringBuilder partialMessage = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            connected = true;
            SRConMod.LOGGER.info("[SRCon] WebSocket 已连接: {}", wsUrl);
            // 发送认证消息
            String auth = "{\"type\":\"auth\",\"server\":\"" + escapeJson(serverId)
                    + "\",\"server_name\":\"" + escapeJson(serverName)
                    + "\",\"token\":\"" + escapeJson(token) + "\"}";
            webSocket.sendText(auth, true);
            sendServerStart();
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partialMessage.append(data);
            if (last) {
                String message = partialMessage.toString();
                partialMessage.setLength(0);
                handleMessage(message);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            connected = false;
            SRConMod.LOGGER.info("[SRCon] WebSocket 断开: code={}, reason={}", statusCode, reason);
            scheduleReconnect();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            SRConMod.LOGGER.error("[SRCon] WebSocket 错误: {}", error.getMessage());
        }

        private void handleMessage(String message) {
            SRConMod.LOGGER.info("[SRCon] 收到消息: {}", message);
            // 兼容 Python json.dumps 的 "key": "value" 带空格格式，统一为紧凑格式
            message = message.replaceAll("\"\\s*:\\s*\"", "\":\"");
            try {
                if (message.contains("\"type\":\"group_chat\"")) {
                    String gmsg = extractJsonValue(message, "msg");
                    if (gmsg != null && !gmsg.isEmpty()) {
                        var gserver = ServerLifecycleHooks.getCurrentServer();
                        if (gserver != null) {
                            String esc = gmsg.replace("\\", "\\\\").replace("\"", "\\\"");
                            String tellraw = "tellraw @a {\"text\":\"" + esc + "\"}";
                            gserver.m_129892_().m_230957_(gserver.m_129893_(), tellraw);
                            SRConMod.LOGGER.info("[SRCon] 群聊广播: {}", gmsg);
                        }
                    }
                }
                if (message.contains("\"type\":\"command\"")) {
                    String cmd = extractJsonValue(message, "cmd");
                    String ackId = extractJsonValue(message, "ack_id");
                    if (cmd != null && !cmd.isEmpty()) {
                        SRConMod.LOGGER.info("[SRCon] 执行命令: /{}", cmd);
                        var server = ServerLifecycleHooks.getCurrentServer();
                        if (server != null) {
                            server.m_129892_().m_230957_(
                                server.m_129893_(), cmd
                            );
                            // 反馈执行结果
                            if (ackId != null) {
                                sendMessage("{\"type\":\"command_result\",\"server\":\"" + escapeJson(serverId)
                                        + "\",\"ack_id\":\"" + escapeJson(ackId) + "\",\"ok\":true}");
                            }
                        }
                    }
                }
            } catch (Exception e) {
                SRConMod.LOGGER.error("[SRCon] 处理消息失败: {}", e.getMessage());
            }
        }

        private String extractJsonValue(String json, String key) {
            String search = "\"" + key + "\":\"";
            int start = json.indexOf(search);
            if (start == -1) return null;
            start += search.length();
            int end = json.indexOf("\"", start);
            if (end == -1) return null;
            return json.substring(start, end);
        }
    }
}
