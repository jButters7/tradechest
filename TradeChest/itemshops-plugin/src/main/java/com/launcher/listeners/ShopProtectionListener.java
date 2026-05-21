package com.launcher.listeners;

import com.launcher.Main;
import com.launcher.models.Shop;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class ShopProtectionListener implements Listener {

    private final Main plugin;

    public ShopProtectionListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        // Player breaking the shop chest itself
        if (block.getType() == Material.CHEST && plugin.getShopManager().isShop(block.getLocation())) {
            Shop shop = plugin.getShopManager().getShop(block.getLocation());
            if (!shop.isOwner(player) && !player.hasPermission("tradechest.admin")) {
                event.setCancelled(true);
                player.sendMessage(plugin.getConfigManager().msgNotOwner());
                return;
            }
            // Owner or admin — remove the shop, then let the break proceed
            plugin.getShopManager().removeShop(block.getLocation());
            player.sendMessage(plugin.getConfigManager().msgShopRemoved());
            return;
        }

        // Player breaking a sign attached to a shop
        for (Shop shop : plugin.getShopManager().getShops().values()) {
            if (shop.getSign() != null && block.getLocation().equals(shop.getSign().getLocation())) {
                if (!shop.isOwner(player) && !player.hasPermission("tradechest.admin")) {
                    event.setCancelled(true);
                    player.sendMessage(plugin.getConfigManager().msgNotOwner());
                } else {
                    shop.setSign(null);
                }
                return;
            }
        }

        // Generic protection radius
        if (isProtectedBlock(block.getLocation(), player)) {
            event.setCancelled(true);
            player.sendMessage(plugin.getConfigManager().msgProtectedArea());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isProtectedBlock(event.getBlock().getLocation(), event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getConfigManager().msgProtectedArea());
        }
    }

    /**
     * Returns true if the given location is within the protection radius of any shop
     * AND the block is not a naturally accessible position (below the chest, or
     * directly behind it — players need to be able to stand and access their chest).
     */
    private boolean isProtectedBlock(Location loc, Player player) {
        if (player.hasPermission("tradechest.admin")) return false;
        int radius = plugin.getConfigManager().getProtectionRadius();
        if (!plugin.getShopManager().isNearShop(loc, radius)) return false;

        for (Location shopLoc : plugin.getShopManager().getShops().keySet()) {
            if (shopLoc.getWorld() == null || !shopLoc.getWorld().equals(loc.getWorld())) continue;
            if (shopLoc.distance(loc) > radius) continue;

            // Allow breaking blocks below the shop level
            if (loc.getBlockY() < shopLoc.getBlockY()) return false;

            // Allow breaking the block directly behind the chest (player access)
            Block chestBlock = shopLoc.getBlock();
            BlockFace facing = chestBlock.getBlockData() instanceof Directional d ? d.getFacing() : BlockFace.NORTH;
            Block behind = chestBlock.getRelative(getOpposite(facing));
            if (behind.getLocation().equals(loc)) return false;
        }
        return true;
    }

    private BlockFace getOpposite(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.NORTH;
            case EAST -> BlockFace.WEST;
            case WEST -> BlockFace.EAST;
            case UP -> BlockFace.DOWN;
            case DOWN -> BlockFace.UP;
            default -> face;
        };
    }
}
