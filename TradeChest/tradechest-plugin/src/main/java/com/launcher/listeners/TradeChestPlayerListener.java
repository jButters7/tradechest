package com.launcher.listeners;

import com.launcher.Main;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class TradeChestPlayerListener implements Listener {

    private final Main plugin;

    public TradeChestPlayerListener(Main plugin) {
        this.plugin = plugin;
    }

    /**
     * When a player logs out, hide all hologram stands from them so their
     * client doesn't hold stale entity references on their next login.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getTradeChestManager().hideAllFrom(event.getPlayer());
    }

    /**
     * When a player changes worlds, hide all stands — the proximity task
     * will re-show the correct ones within a second.
     */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        plugin.getTradeChestManager().hideAllFrom(event.getPlayer());
    }
}
