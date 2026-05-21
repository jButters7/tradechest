package com.launcher.gui;

import com.launcher.Main;
import com.launcher.models.Shop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ShopManagementGUI implements Listener {

    private final Main plugin;
    private final Player player;
    private final Shop shop;

    public ShopManagementGUI(Main plugin, Player player, Shop shop) {
        this.plugin = plugin;
        this.player = player;
        this.shop = shop;
    }

    private static final Component TITLE = Component.text("Shop Settings").color(NamedTextColor.GOLD);

    public void open() {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        build(inv);
        player.openInventory(inv);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void build(Inventory inv) {
        ItemStack glass = makeGlass();
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);

        // Toggle hologram
        inv.setItem(10, makeToggle("Hologram", shop.isHologramVisible(), Material.GLOWSTONE, Material.GRAY_CONCRETE));

        // Toggle notifications
        inv.setItem(12, makeToggle("Notifications", shop.isNotificationsEnabled(), Material.BELL, Material.GRAY_CONCRETE));

        // Remove shop
        ItemStack remove = new ItemStack(Material.BARRIER);
        var meta = remove.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Remove Shop").color(NamedTextColor.RED));
            meta.lore(List.of(Component.text("Permanently deletes this shop.").color(NamedTextColor.GRAY)));
            remove.setItemMeta(meta);
        }
        inv.setItem(22, remove);

        // Shop info
        ItemStack info = new ItemStack(Material.BOOK);
        var infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.displayName(Component.text("Shop Info").color(NamedTextColor.AQUA));
            infoMeta.lore(List.of(
                Component.text("Selling: " + shop.getSellingAmount() + "x " + Shop.formatItemName(shop.getSellingItem().getType())).color(NamedTextColor.WHITE),
                Component.text("Price: " + shop.getPriceAmount() + "x " + Shop.formatItemName(shop.getPriceItem().getType())).color(NamedTextColor.WHITE),
                Component.text("Total sales: " + shop.getTotalSales()).color(NamedTextColor.WHITE)
            ));
            info.setItemMeta(infoMeta);
        }
        inv.setItem(4, info);
    }

    private ItemStack makeToggle(String label, boolean on, Material onMat, Material offMat) {
        ItemStack item = new ItemStack(on ? onMat : offMat);
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(label + ": ").color(NamedTextColor.WHITE)
                    .append(Component.text(on ? "ON" : "OFF")
                            .color(on ? NamedTextColor.GREEN : NamedTextColor.RED)));
            meta.lore(List.of(Component.text("Click to toggle").color(NamedTextColor.GRAY)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeGlass() {
        ItemStack g = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var m = g.getItemMeta();
        if (m != null) { m.displayName(Component.empty()); g.setItemMeta(m); }
        return g;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p) || !p.equals(player)) return;
        if (!event.getView().title().equals(TITLE)) return;
        event.setCancelled(true);

        switch (event.getRawSlot()) {
            case 10 -> {
                shop.setHologramVisible(!shop.isHologramVisible());
                if (shop.isHologramVisible()) shop.createHologram(plugin.getConfigManager().getHologramHeight());
                else shop.removeHologram();
                build(event.getInventory());
            }
            case 12 -> {
                shop.setNotificationsEnabled(!shop.isNotificationsEnabled());
                build(event.getInventory());
            }
            case 22 -> {
                closeAndUnregister();
                plugin.getShopManager().removeShop(shop.getChestLocation());
                player.sendMessage(plugin.getConfigManager().msgShopRemoved());
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer().equals(player)) closeAndUnregister();
    }

    private void closeAndUnregister() {
        InventoryClickEvent.getHandlerList().unregister(this);
        InventoryCloseEvent.getHandlerList().unregister(this);
    }
}
