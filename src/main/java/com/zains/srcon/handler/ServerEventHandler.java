package com.zains.srcon.handler;

import com.zains.srcon.SRConMod;
import com.zains.srcon.network.WebSocketClient;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ServerEventHandler {
    private final WebSocketClient wsClient;


    public ServerEventHandler(WebSocketClient wsClient) {
        this.wsClient = wsClient;
        registerBukkitChatListener();
    }

    /** Mohist 混合端中 Forge ServerChatEvent 不触发，改用 Bukkit AsyncPlayerChatEvent（纯反射） */
    private void registerBukkitChatListener() {
        try {
            Class<?> cBukkit = Class.forName("org.bukkit.Bukkit");
            Object pm = cBukkit.getMethod("getPluginManager").invoke(null);
            Object[] plugins = (Object[]) pm.getClass().getMethod("getPlugins").invoke(pm);
            if (plugins == null || plugins.length == 0) {
                SRConMod.LOGGER.warn("[SRCon] 无 Bukkit 插件宿主，无法注册聊天监听");
                return;
            }
            Object host = plugins[0];
            Class<?> cExecutor = Class.forName("org.bukkit.plugin.EventExecutor");
            Object executor = java.lang.reflect.Proxy.newProxyInstance(
                cExecutor.getClassLoader(), new Class[]{cExecutor}, (p, m, a) -> {
                    if (m.getName().equals("execute")) {
                        Object ev = a[1];
                        if (ev.getClass().getName().equals("org.bukkit.event.player.AsyncPlayerChatEvent")) {
                            try {
                                boolean cancelled = (boolean) ev.getClass().getMethod("isCancelled").invoke(ev);
                                if (!cancelled) {
                                    Object player = ev.getClass().getMethod("getPlayer").invoke(ev);
                                    String pname = (String) player.getClass().getMethod("getName").invoke(player);
                                    String msg = (String) ev.getClass().getMethod("getMessage").invoke(ev);
                                    wsClient.sendChat(pname, msg);
                                    SRConMod.LOGGER.info("[SRCon] 聊天(Bukkit): {} -> {}", pname, msg);
                                }
                            } catch (Exception ex) {
                                SRConMod.LOGGER.error("[SRCon] Bukkit chat 处理异常", ex);
                            }
                        }
                    }
                    return null;
                });
            Class<?> cListener = Class.forName("org.bukkit.event.Listener");
            Object listener = java.lang.reflect.Proxy.newProxyInstance(
                cListener.getClassLoader(), new Class[]{cListener}, (p, m, a) -> null);
            Class<?> cPriority = Class.forName("org.bukkit.event.EventPriority");
            Object normal = Enum.valueOf((Class<Enum>) cPriority, "NORMAL");
            Class<?> cEvent = Class.forName("org.bukkit.event.player.AsyncPlayerChatEvent");
            Class<?> cPlugin = Class.forName("org.bukkit.plugin.Plugin");
            pm.getClass().getMethod("registerEvent", Class.class, cListener, cPriority, cExecutor, cPlugin)
                .invoke(pm, cEvent, listener, normal, executor, host);
            SRConMod.LOGGER.info("[SRCon] Bukkit 聊天监听已注册 (宿主 {})", host.getClass().getName());
        } catch (Exception e) {
            SRConMod.LOGGER.error("[SRCon] Bukkit 聊天监听注册失败: {}", e.toString());
        }
    }


    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        String player = event.getPlayer().m_6302_();
        String message = event.getMessage().getString();
        wsClient.sendChat(player, message);
        SRConMod.LOGGER.info("[SRCon] 聊天: {} -> {}", player, message);
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        String player = event.getEntity().m_6302_();
        SRConMod.LOGGER.info("[SRCon] 玩家加入: {}", player);
        wsClient.sendJoin(player);
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        String player = event.getEntity().m_6302_();
        SRConMod.LOGGER.info("[SRCon] 玩家退出: {}", player);
        wsClient.sendLeave(player);
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            String playerName = player.m_6302_();
            DamageSource source = event.getSource();
            String reason = source.m_6157_(player).getString();
            SRConMod.LOGGER.info("[SRCon] 玩家死亡: {} ({})", playerName, reason);
            wsClient.sendDeath(playerName, reason);
        }
    }
}
