package com.launcher.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Reads values from config.yml and provides typed accessors.
 * Uses Adventure MiniMessage for all player-facing text — no legacy §-codes.
 */
public class TradeChestConfigManager {

    private final JavaPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public TradeChestConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
    }

    // ── Prefix ───────────────────────────────────────────────────────────────

    private Component prefix() {
        String raw = plugin.getConfig().getString("prefix", "<dark_gray>[<gold>TradeChest<dark_gray>]");
        return mm.deserialize(raw);
    }

    /**
     * Returns a full chat message with the plugin prefix prepended.
     * Supports MiniMessage tags in the template, plus named placeholders.
     * Example: msg("shop-created", Placeholder.unparsed("name", "MyShop"))
     */
    public Component msg(String template, TagResolver... resolvers) {
        Component body = mm.deserialize(template, resolvers);
        return prefix().append(Component.text(" ")).append(body);
    }

    // ── Shop settings ─────────────────────────────────────────────────────────

    public int getMaxShopsPerPlayer() {
        return plugin.getConfig().getInt("shop.max-shops-per-player", 5);
    }

    public int getProtectionRadius() {
        return plugin.getConfig().getInt("shop.protection-radius", 1);
    }

    public int getMaxPurchaseQuantity() {
        return plugin.getConfig().getInt("shop.max-purchase-quantity", 64);
    }

    public double getHologramHeight() {
        return plugin.getConfig().getDouble("shop.hologram-height", 2.0);
    }

    public int getAutosaveInterval() {
        return plugin.getConfig().getInt("shop.autosave-interval", 300);
    }

    public boolean isSaleNotificationsDefault() {
        return plugin.getConfig().getBoolean("shop.sale-notifications", true);
    }

    // ── Pre-built messages ────────────────────────────────────────────────────
    // These return Component directly so callers just do player.sendMessage(cfg.msgXxx())

    public Component msgShopAlreadyExists() {
        return msg("<red>A shop already exists at that chest.");
    }

    public Component msgShopCreated() {
        return msg("<green>Shop created! Left-click the chest to let buyers purchase from it.");
    }

    public Component msgShopRemoved() {
        return msg("<green>Shop removed.");
    }

    public Component msgNotOwner() {
        return msg("<red>You don't own that shop.");
    }

    public Component msgProtectedArea() {
        return msg("<red>That block is protected by a nearby shop.");
    }

    public Component msgShopDoesntExist() {
        return msg("<red>That shop no longer exists.");
    }

    public Component msgNotEnoughStock() {
        return msg("<red>The shop doesn't have enough stock for that quantity.");
    }

    public Component msgNotEnoughPayment(int amount, String item) {
        return msg("<red>You need <white><amount>x <item></white> to make that purchase.",
                Placeholder.unparsed("amount", String.valueOf(amount)),
                Placeholder.unparsed("item", item));
    }

    public Component msgPurchaseSuccess(int sellAmt, String sellItem, int priceAmt, String priceItem) {
        return msg("<green>Purchased <white><sa>x <si></white> for <white><pa>x <pi></white>.",
                Placeholder.unparsed("sa", String.valueOf(sellAmt)),
                Placeholder.unparsed("si", sellItem),
                Placeholder.unparsed("pa", String.valueOf(priceAmt)),
                Placeholder.unparsed("pi", priceItem));
    }

    public Component msgSaleNotification(String buyer, int sellAmt, String sellItem, int priceAmt, String priceItem) {
        return msg("<gold><buyer></gold> bought <white><sa>x <si></white> for <white><pa>x <pi></white>.",
                Placeholder.unparsed("buyer", buyer),
                Placeholder.unparsed("sa", String.valueOf(sellAmt)),
                Placeholder.unparsed("si", sellItem),
                Placeholder.unparsed("pa", String.valueOf(priceAmt)),
                Placeholder.unparsed("pi", priceItem));
    }

    public Component msgShopLimitReached(int limit) {
        return msg("<red>You've reached your shop limit of <limit>.",
                Placeholder.unparsed("limit", String.valueOf(limit)));
    }

    public Component msgNoPermission() {
        return msg("<red>You don't have permission to do that.");
    }

    public Component msgReloaded() {
        return msg("<green>TradeChest reloaded.");
    }

    public Component msgInvalidNumber() {
        return msg("<red>That's not a valid number.");
    }

    public Component msgNotAChest() {
        return msg("<red>You must be looking at a chest to create a shop.");
    }

    public Component msgHoldItem() {
        return msg("<red>Hold the item you want to sell in your main hand.");
    }

}