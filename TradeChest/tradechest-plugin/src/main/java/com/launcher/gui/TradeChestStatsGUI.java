package com.launcher.gui;

import com.launcher.Main;
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

public class TradeChestStatsGUI implements Listener {

    private final Main plugin;
    private final Player player;
    private static final Component TITLE = Component.text("Your Statistics").color(NamedTextColor.GOLD);

    public TradeChestStatsGUI(Main plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

        int shopCount  = plugin.getTradeChestManager().getPlayerShopCount(player.getUniqueId());
        int totalSales = plugin.getTradeChestManager().getTotalPlayerSales(player.getUniqueId());
        int shopLimit  = plugin.getTradeChestManager().getPlayerShopLimit(player.getUniqueId());

        // Fill glass background
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var gm = glass.getItemMeta();
        if (gm != null) { gm.displayName(Component.empty()); glass.setItemMeta(gm); }
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);

        // Shop count
        ItemStack shopItem = new ItemStack(Material.CHEST);
        var sm = shopItem.getItemMeta();
        if (sm != null) {
            sm.displayName(Component.text("Shops").color(NamedTextColor.YELLOW));
            sm.lore(List.of(
                Component.text(shopCount + " / " + (shopLimit <= 0 ? "∞" : shopLimit))
                    .color(NamedTextColor.WHITE)
            ));
            shopItem.setItemMeta(sm);
        }
        inv.setItem(11, shopItem);

        // Total sales
        ItemStack salesItem = new ItemStack(Material.GOLD_INGOT);
        var salm = salesItem.getItemMeta();
        if (salm != null) {
            salm.displayName(Component.text("Total Sales").color(NamedTextColor.YELLOW));
            salm.lore(List.of(
                Component.text(String.valueOf(totalSales)).color(NamedTextColor.WHITE)
            ));
            salesItem.setItemMeta(salm);
        }
        inv.setItem(13, salesItem);

        player.openInventory(inv);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked().equals(player) && e.getView().title().equals(TITLE))
            e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer().equals(player)) {
            InventoryClickEvent.getHandlerList().unregister(this);
            InventoryCloseEvent.getHandlerList().unregister(this);
        }
    }
}
