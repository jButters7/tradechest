package com.launcher.listeners;

import com.launcher.Main;
import com.launcher.models.Shop;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Left-clicking a shop chest immediately executes one trade.
 * No chat prompt, no pending state — just click and buy.
 */
public class ChestInteractListener implements Listener {

    private final Main plugin;

    public ChestInteractListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHEST) return;

        Player player = event.getPlayer();
        if (!plugin.getShopManager().isShop(block.getLocation())) return;

        Shop shop = plugin.getShopManager().getShop(block.getLocation());

        // Owner left-clicking their own shop does nothing — they use /shop settings
        if (shop.isOwner(player)) return;

        event.setCancelled(true);

        // Execute one trade immediately — no quantity prompt
        plugin.getShopManager().processPurchase(player, block.getLocation());
    }
}
