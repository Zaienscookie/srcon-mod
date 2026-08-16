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
        wsClient.sendJoin(player);
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        String player = event.getEntity().m_6302_();
        wsClient.sendLeave(player);
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            String playerName = player.m_6302_();
            DamageSource source = event.getSource();
            String reason = source.m_6157_(player).getString();
            wsClient.sendDeath(playerName, reason);
        }
    }
}
