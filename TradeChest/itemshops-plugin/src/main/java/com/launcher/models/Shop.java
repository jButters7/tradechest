package com.launcher.models;

import com.launcher.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a single player-owned chest shop.
 * Holograms use invisible ArmorStands; signs use Adventure Components.
 */
public class Shop {

    // ── Identity ──────────────────────────────────────────────────────────────
    private final UUID ownerUUID;
    private final String ownerName;
    private final Location chestLocation;

    // ── Trade ─────────────────────────────────────────────────────────────────
    private ItemStack sellingItem;
    private int sellingAmount;
    private ItemStack priceItem;
    private int priceAmount;

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean hologramVisible = true;
    private boolean notificationsEnabled = true;
    private int totalSales = 0;

    // ── World objects ─────────────────────────────────────────────────────────
    private final List<ArmorStand> holograms = new ArrayList<>();
    private Sign sign = null;

    // ── Marker tag stored in ArmorStand's PersistentDataContainer ─────────────
    // (avoids string-matching holograms by name — much safer)
    public static final org.bukkit.NamespacedKey HOLOGRAM_KEY =
            new org.bukkit.NamespacedKey("tradechest", "shop_hologram");

    public Shop(UUID ownerUUID, String ownerName, Location chestLocation,
                ItemStack sellingItem, int sellingAmount,
                ItemStack priceItem, int priceAmount) {
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.chestLocation = chestLocation;
        this.sellingItem = sellingItem;
        this.sellingAmount = sellingAmount;
        this.priceItem = priceItem;
        this.priceAmount = priceAmount;
    }

    public Shop(Player player, Location chestLocation,
                ItemStack sellingItem, int sellingAmount,
                ItemStack priceItem, int priceAmount) {
        this(player.getUniqueId(), player.getName(), chestLocation,
                sellingItem, sellingAmount, priceItem, priceAmount);
    }

    // ── Hologram ──────────────────────────────────────────────────────────────

    public void createHologram(double height) {
        if (!hologramVisible) return;
        removeHologram();

        var world = chestLocation.getWorld();
        if (world == null) return;

        double x = chestLocation.getX() + 0.5;
        double z = chestLocation.getZ() + 0.5;
        double base = chestLocation.getY() + height;

        String[] lines = {
            "§6§lPlayer Shop",
            "§eSelling " + sellingAmount + "x " + formatItemName(sellingItem.getType())
                + " §6for " + priceAmount + "x " + formatItemName(priceItem.getType()),
            "§7Left-click to purchase  §f| §7Sales: §f" + totalSales
        };

        double[] offsets = {0.1, -0.2, -0.5};
        for (int i = 0; i < lines.length; i++) {
            Location loc = new Location(world, x, base + offsets[i], z);
            ArmorStand as = world.spawn(loc, ArmorStand.class, stand -> {
                stand.customName(Component.text(lines[i < lines.length ? i : 0]));
                stand.setCustomNameVisible(true);
                stand.setGravity(false);
                stand.setVisible(false);
                stand.setSmall(true);
                stand.setInvulnerable(true);
                stand.getPersistentDataContainer().set(
                        HOLOGRAM_KEY,
                        org.bukkit.persistence.PersistentDataType.STRING,
                        chestLocation.toString());
            });
            // Set actual name after spawn to work around lambda ordering
            String line = lines[i];
            as.customName(Component.text(line));
            holograms.add(as);
        }
    }

    public void removeHologram() {
        holograms.forEach(as -> { if (as != null && !as.isDead()) as.remove(); });
        holograms.clear();

        // Clean up any stranded hologram stands near this chest
        if (chestLocation.getWorld() == null) return;
        for (Entity e : chestLocation.getWorld().getNearbyEntities(chestLocation, 2, 3, 2)) {
            if (e instanceof ArmorStand as) {
                var pdc = as.getPersistentDataContainer();
                if (pdc.has(HOLOGRAM_KEY, org.bukkit.persistence.PersistentDataType.STRING)) {
                    as.remove();
                }
            }
        }
    }

    public void updateHologram(double height) {
        if (hologramVisible) {
            removeHologram();
            createHologram(height);
        }
    }

    /**
     * Show this shop's hologram to a specific player.
     * Called by the proximity task when they enter range.
     */
    public void showTo(org.bukkit.entity.Player player) {
        for (ArmorStand as : holograms) {
            if (as != null && !as.isDead()) {
                player.showEntity(com.launcher.Main.getInstance(), as);
            }
        }
    }

    /**
     * Hide this shop's hologram from a specific player.
     * Called by the proximity task when they leave range.
     */
    public void hideFrom(org.bukkit.entity.Player player) {
        for (ArmorStand as : holograms) {
            if (as != null && !as.isDead()) {
                player.hideEntity(com.launcher.Main.getInstance(), as);
            }
        }
    }

    // ── Sign ──────────────────────────────────────────────────────────────────

    public void createSign() {
        if (sign != null && sign.getBlock().getType().name().contains("SIGN")) return;

        Block chestBlock = chestLocation.getBlock();
        var blockData = chestBlock.getBlockData();

        BlockFace facing = BlockFace.NORTH;
        if (blockData instanceof org.bukkit.block.data.Directional dir) {
            facing = dir.getFacing();
        }

        Block target = chestBlock.getRelative(facing);
        if (target.getType() != Material.AIR) {
            // Try other faces
            for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
                Block rel = chestBlock.getRelative(face);
                if (rel.getType() == Material.AIR) {
                    target = rel;
                    facing = face;
                    break;
                }
            }
        }

        if (target.getType() != Material.AIR) return; // no room for a sign

        target.setType(Material.OAK_WALL_SIGN);
        if (target.getBlockData() instanceof WallSign ws) {
            ws.setFacing(facing);
            target.setBlockData(ws);
        }

        if (target.getState() instanceof Sign s) {
            writeSignLines(s);
            s.update();
            sign = s;
        }
    }

    public void updateSign() {
        if (sign == null) { createSign(); return; }
        // Re-fetch the block state in case it changed
        if (sign.getBlock().getState() instanceof Sign s) {
            writeSignLines(s);
            s.update();
        }
    }

    private void writeSignLines(Sign s) {
        // Adventure Component API — no §-codes
        s.line(0, Component.text("Player's Shop").color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true));
        s.line(1, Component.text(ownerName).color(NamedTextColor.GRAY));
        s.line(2, Component.text("Sell: " + sellingAmount + "x " + formatItemName(sellingItem.getType()))
                .color(NamedTextColor.WHITE));
        s.line(3, Component.text("Price: " + priceAmount + "x " + formatItemName(priceItem.getType()))
                .color(NamedTextColor.WHITE));
    }

    public void removeSign() {
        if (sign != null) {
            sign.getBlock().setType(Material.AIR);
            sign = null;
        }
    }

    // ── Inventory helpers ─────────────────────────────────────────────────────

    /**
     * Does the chest have at least (sellingAmount * quantity) of the selling item,
     * matching type AND item meta (enchants, custom name, etc.)?
     */
    public boolean hasEnoughStock(Inventory inv, int quantity) {
        int required = sellingAmount * quantity;
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.isSimilar(sellingItem)) {
                required -= item.getAmount();
                if (required <= 0) return true;
            }
        }
        return false;
    }

    public boolean hasEnoughStock(Inventory inv) {
        return hasEnoughStock(inv, 1);
    }

    /**
     * Does the player have at least (amount) of priceItem, matching meta?
     */
    public boolean playerCanPay(Player player, int quantity) {
        int required = priceAmount * quantity;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.isSimilar(priceItem)) {
                required -= item.getAmount();
                if (required <= 0) return true;
            }
        }
        return false;
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    public boolean isOwner(Player player) {
        return player.getUniqueId().equals(ownerUUID);
    }


    public static String formatItemName(Material material) {
        if (material == null) return "Unknown";
        String name = material.name().toLowerCase().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        boolean cap = true;
        for (char c : name.toCharArray()) {
            if (cap && Character.isLetter(c)) { sb.append(Character.toUpperCase(c)); cap = false; }
            else { sb.append(c); if (c == ' ') cap = true; }
        }
        return sb.toString();
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public UUID getOwnerUUID() { return ownerUUID; }
    public String getOwnerName() { return ownerName; }
    public Location getChestLocation() { return chestLocation; }

    public ItemStack getSellingItem() { return sellingItem; }
    public void setSellingItem(ItemStack i) { sellingItem = i; }
    public int getSellingAmount() { return sellingAmount; }
    public void setSellingAmount(int a) { sellingAmount = a; }

    public ItemStack getPriceItem() { return priceItem; }
    public void setPriceItem(ItemStack i) { priceItem = i; }
    public int getPriceAmount() { return priceAmount; }
    public void setPriceAmount(int a) { priceAmount = a; }

    public boolean isHologramVisible() { return hologramVisible; }
    public void setHologramVisible(boolean v) { hologramVisible = v; }
    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public void setNotificationsEnabled(boolean v) { notificationsEnabled = v; }

    public int getTotalSales() { return totalSales; }
    public void setTotalSales(int s) { totalSales = s; }
    public void incrementTotalSales() { totalSales++; }

    public List<ArmorStand> getHolograms() { return holograms; }
    public Sign getSign() { return sign; }
    public void setSign(Sign s) { sign = s; }
}
