package com.zains.srcon;

import com.zains.srcon.network.WebSocketClient;
import com.zains.srcon.handler.ServerEventHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(SRConMod.MODID)
public class SRConMod {
    public static final String MODID = "srcon";
    public static final Logger LOGGER = LogManager.getLogger();
    
    private static SRConMod instance;
    private WebSocketClient wsClient;
    private String serverId = "s1";
    private String serverName = "生存服";
    private String wsUrl = "ws://127.0.0.1:8765";
    private String token = "srcon_default_token";

    public SRConMod() {
        instance = this;
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("[SRCon] 群服互通 Mod 初始化");
        // 读取配置
        loadConfig();
    }

    private void loadConfig() {
        // 从环境变量或配置文件读取
        String envServerId = System.getenv("SRCON_SERVER_ID");
        String envServerName = System.getenv("SRCON_SERVER_NAME");
        String envWsUrl = System.getenv("SRCON_WS_URL");
        String envToken = System.getenv("SRCON_TOKEN");
        
        if (envServerId != null) serverId = envServerId;
        if (envServerName != null) serverName = envServerName;
        if (envWsUrl != null) wsUrl = envWsUrl;
        if (envToken != null) token = envToken;
        
        LOGGER.info("[SRCon] 配置: server={}({}), ws={}", serverId, serverName, wsUrl);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[SRCon] 服务器启动，连接 WebSocket...");
        wsClient = new WebSocketClient(wsUrl, serverId, serverName, token);
        wsClient.connect();
        
        // 注册事件处理器
        MinecraftForge.EVENT_BUS.register(new ServerEventHandler(wsClient));
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("[SRCon] 服务器关闭，断开 WebSocket...");
        if (wsClient != null) {
            wsClient.sendMessage("{\"type\":\"server_stop\",\"server\":\"" + serverId + "\"}");
            wsClient.close();
        }
    }

    public static SRConMod getInstance() { return instance; }
    public WebSocketClient getWsClient() { return wsClient; }
    public String getServerId() { return serverId; }
    public String getServerName() { return serverName; }
}
