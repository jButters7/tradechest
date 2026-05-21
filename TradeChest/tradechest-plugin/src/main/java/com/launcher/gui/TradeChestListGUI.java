package com.launcher.gui;

import com.launcher.Main;
import com.launcher.models.TradeChest;
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

/**
 * Simple read-only GUI showing the player's own shops.
 */
public class TradeChestListGUI implements Listener {

    private final Main plugin;
    private final Player player;
    private static final Component TITLE = Component.text("My Shops").color(NamedTextColor.GOLD);

    public TradeChestListGUI(Main plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        List<TradeChest> shops = plugin.getTradeChestManager().getPlayerShops(player.getUniqueId());
        int size = Math.max(9, ((shops.size() / 9) + 1) * 9);
        Inventory inv = Bukkit.createInventory(null, Math.min(size, 54), TITLE);

        for (int i = 0; i < shops.size() && i < 54; i++) {
            TradeChest shop = shops.get(i);
            ItemStack item = shop.getSellingItem().clone();
            item.setAmount(1);
            var meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text("Shop #" + (i + 1)).color(NamedTextColor.YELLOW));
                meta.lore(List.of(
                    Component.text("Selling: " + shop.getSellingAmount() + "x " + TradeChest.formatItemName(shop.getSellingItem().getType())).color(NamedTextColor.WHITE),
                    Component.text("Price: " + shop.getPriceAmount() + "x " + TradeChest.formatItemName(shop.getPriceItem().getType())).color(NamedTextColor.WHITE),
                    Component.text("Sales: " + shop.getTotalSales()).color(NamedTextColor.GRAY)
                ));
                item.setItemMeta(meta);
            }
            inv.setItem(i, item);
        }

        if (shops.isEmpty()) {
            ItemStack barrier = new ItemStack(Material.BARRIER);
            var m = barrier.getItemMeta();
            if (m != null) { m.displayName(Component.text("You have no shops.").color(NamedTextColor.RED)); barrier.setItemMeta(m); }
            inv.setItem(4, barrier);
        }

        player.openInventory(inv);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked().equals(player) && e.getView().title().equals(TITLE)) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer().equals(player)) {
            InventoryClickEvent.getHandlerList().unregister(this);
            InventoryCloseEvent.getHandlerList().unregister(this);
        }
    }
}
