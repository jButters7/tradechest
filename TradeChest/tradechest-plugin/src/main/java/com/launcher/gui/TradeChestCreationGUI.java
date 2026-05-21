package com.launcher.gui;

import com.launcher.Main;
import com.launcher.models.TradeChest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * GUI for setting sell amount, price item, and price amount before confirming shop creation.
 */
public class TradeChestCreationGUI implements Listener {

    private static final int SLOT_SELLING_ITEM = 10;
    private static final int SLOT_PRICE_ITEM   = 16;
    private static final int SLOT_CONFIRM       = 13;
    private static final int SLOT_CANCEL        = 22;

    private final Main plugin;
    private final Player player;
    private final Location chestLocation;
    private final ItemStack sellingItem;

    private ItemStack priceItem = new ItemStack(Material.EMERALD);
    private int sellingAmount = 1;
    private int priceAmount = 1;

    public TradeChestCreationGUI(Main plugin, Player player, Location chestLocation, ItemStack sellingItem) {
        this.plugin = plugin;
        this.player = player;
        this.chestLocation = chestLocation;
        this.sellingItem = sellingItem;
    }

    public void open() {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text("Create Shop").color(NamedTextColor.GOLD));
        buildInventory(inv);
        player.openInventory(inv);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void buildInventory(Inventory inv) {
        // Fill background
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var meta = glass.getItemMeta();
        if (meta != null) { meta.displayName(Component.empty()); glass.setItemMeta(meta); }
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);

        // Selling item display
        ItemStack displaySell = sellingItem.clone();
        displaySell.setAmount(sellingAmount);
        var sellMeta = displaySell.getItemMeta();
        if (sellMeta != null) {
            sellMeta.displayName(Component.text("Selling: " + sellingAmount + "x "
                    + TradeChest.formatItemName(sellingItem.getType())).color(NamedTextColor.YELLOW));
            sellMeta.lore(List.of(
                Component.text("Left-click: +1  Right-click: -1").color(NamedTextColor.GRAY),
                Component.text("Shift+click: +10 / -10").color(NamedTextColor.GRAY)
            ));
            displaySell.setItemMeta(sellMeta);
        }
        inv.setItem(SLOT_SELLING_ITEM, displaySell);

        // Price item display
        ItemStack displayPrice = priceItem.clone();
        displayPrice.setAmount(priceAmount);
        var priceMeta = displayPrice.getItemMeta();
        if (priceMeta != null) {
            priceMeta.displayName(Component.text("Price: " + priceAmount + "x "
                    + TradeChest.formatItemName(priceItem.getType())).color(NamedTextColor.AQUA));
            priceMeta.lore(List.of(
                Component.text("Left-click: +1  Right-click: -1").color(NamedTextColor.GRAY),
                Component.text("Shift+click: +10 / -10").color(NamedTextColor.GRAY),
                Component.text("Drop key: change price item").color(NamedTextColor.GRAY)
            ));
            displayPrice.setItemMeta(priceMeta);
        }
        inv.setItem(SLOT_PRICE_ITEM, displayPrice);

        // Confirm button
        ItemStack confirm = new ItemStack(Material.LIME_WOOL);
        var confirmMeta = confirm.getItemMeta();
        if (confirmMeta != null) {
            confirmMeta.displayName(Component.text("✔ Confirm").color(NamedTextColor.GREEN));
            confirm.setItemMeta(confirmMeta);
        }
        inv.setItem(SLOT_CONFIRM, confirm);

        // Cancel button
        ItemStack cancel = new ItemStack(Material.RED_WOOL);
        var cancelMeta = cancel.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.displayName(Component.text("✖ Cancel").color(NamedTextColor.RED));
            cancel.setItemMeta(cancelMeta);
        }
        inv.setItem(SLOT_CANCEL, cancel);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p) || !p.equals(player)) return;
        if (event.getView().title().equals(Component.text("Create Shop").color(NamedTextColor.GOLD))) {
            event.setCancelled(true);
            int slot = event.getRawSlot();

            if (slot == SLOT_SELLING_ITEM) {
                int delta = event.isShiftClick() ? 10 : 1;
                if (event.isRightClick()) delta = -delta;
                sellingAmount = Math.max(1, sellingAmount + delta);
                buildInventory(event.getInventory());
            } else if (slot == SLOT_PRICE_ITEM) {
                if (event.isRightClick() && !event.isShiftClick()) {
                    priceAmount = Math.max(1, priceAmount - 1);
                } else if (event.isShiftClick() && event.isRightClick()) {
                    priceAmount = Math.max(1, priceAmount - 10);
                } else if (event.isShiftClick()) {
                    priceAmount += 10;
                } else {
                    priceAmount++;
                }
                buildInventory(event.getInventory());
            } else if (slot == SLOT_CONFIRM) {
                closeAndUnregister();
                plugin.getTradeChestManager().createShop(player, chestLocation,
                        sellingItem, sellingAmount, priceItem, priceAmount);
                player.sendMessage(plugin.getTradeChestConfigManager().msgShopCreated());
            } else if (slot == SLOT_CANCEL) {
                closeAndUnregister();
                player.sendMessage(plugin.getTradeChestConfigManager().msg("<gray>Shop creation cancelled."));
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer().equals(player)) closeAndUnregister();
    }

    private void closeAndUnregister() {
        InventoryClickEvent.getHandlerList().unregister(this);
        InventoryCloseEvent.getHandlerList().unregister(this);
        // Close outside event if not already closed
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.getOpenInventory().title()
                    .equals(Component.text("Create Shop").color(NamedTextColor.GOLD))) {
                player.closeInventory();
            }
        });
    }
}
