package com.launcher.commands;

import com.launcher.Main;
import com.launcher.gui.TradeChestCreationGUI;
import com.launcher.gui.TradeChestManagementGUI;
import com.launcher.gui.TradeChestListGUI;
import com.launcher.gui.TradeChestStatsGUI;
import com.launcher.models.TradeChest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class TradeChestCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;

    public TradeChestCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is player-only.");
            return true;
        }

        if (!player.hasPermission("tradechest.use")) {
            player.sendMessage(plugin.getTradeChestConfigManager().msgNoPermission());
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create"   -> handleCreate(player);
            case "remove"   -> handleRemove(player);
            case "settings" -> handleSettings(player);
            case "shops"    -> handleShops(player);
            case "stats"    -> handleStats(player);
            case "reload"   -> handleReload(player);
            case "admin"    -> handleAdmin(player, args);
            case "help"     -> sendHelp(player);
            default         -> sendHelp(player);
        }
        return true;
    }

    // ── Sub-commands ──────────────────────────────────────────────────────────

    private void handleCreate(Player player) {
        if (!player.hasPermission("tradechest.create")) {
            player.sendMessage(plugin.getTradeChestConfigManager().msgNoPermission());
            return;
        }
        Block target = player.getTargetBlockExact(5);
        if (target == null || target.getType() != Material.CHEST) {
            player.sendMessage(plugin.getTradeChestConfigManager().msgNotAChest());
            return;
        }
        var hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage(plugin.getTradeChestConfigManager().msgHoldItem());
            return;
        }
        new TradeChestCreationGUI(plugin, player, target.getLocation(), hand.clone()).open();
    }

    private void handleRemove(Player player) {
        if (!player.hasPermission("tradechest.remove")) {
            player.sendMessage(plugin.getTradeChestConfigManager().msgNoPermission());
            return;
        }
        Block target = player.getTargetBlockExact(5);
        if (target == null || !plugin.getTradeChestManager().isShop(target.getLocation())) {
            player.sendMessage(plugin.getTradeChestConfigManager().msg("<red>Look at a shop chest to remove it."));
            return;
        }
        TradeChest shop = plugin.getTradeChestManager().getShop(target.getLocation());
        if (!shop.isOwner(player) && !player.hasPermission("tradechest.admin")) {
            player.sendMessage(plugin.getTradeChestConfigManager().msgNotOwner());
            return;
        }
        plugin.getTradeChestManager().removeShop(target.getLocation());
        player.sendMessage(plugin.getTradeChestConfigManager().msgShopRemoved());
    }

    private void handleSettings(Player player) {
        Block target = player.getTargetBlockExact(5);
        if (target == null || !plugin.getTradeChestManager().isShop(target.getLocation())) {
            player.sendMessage(plugin.getTradeChestConfigManager().msg("<red>Look at a shop chest first."));
            return;
        }
        TradeChest shop = plugin.getTradeChestManager().getShop(target.getLocation());
        if (!shop.isOwner(player) && !player.hasPermission("tradechest.admin")) {
            player.sendMessage(plugin.getTradeChestConfigManager().msgNotOwner());
            return;
        }
        new TradeChestManagementGUI(plugin, player, shop).open();
    }

    private void handleShops(Player player) {
        new TradeChestListGUI(plugin, player).open();
    }

    private void handleStats(Player player) {
        new TradeChestStatsGUI(plugin, player).open();
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("tradechest.admin")) {
            player.sendMessage(plugin.getTradeChestConfigManager().msgNoPermission());
            return;
        }
        plugin.reload();
        player.sendMessage(plugin.getTradeChestConfigManager().msgReloaded());
    }

    private void handleAdmin(Player player, String[] args) {
        if (!player.hasPermission("tradechest.admin")) {
            player.sendMessage(plugin.getTradeChestConfigManager().msgNoPermission());
            return;
        }
        if (args.length < 3 || !args[1].equalsIgnoreCase("setlimit")) {
            player.sendMessage(plugin.getTradeChestConfigManager().msg(
                    "<yellow>Usage: /shop admin setlimit <player> <limit>"));
            return;
        }
        String targetName = args[2];
        int limit;
        try { limit = Integer.parseInt(args.length >= 4 ? args[3] : "0"); }
        catch (NumberFormatException e) {
            player.sendMessage(plugin.getTradeChestConfigManager().msgInvalidNumber());
            return;
        }
        var offline = plugin.getServer().getOfflinePlayerIfCached(targetName);
        if (offline == null) {
            player.sendMessage(plugin.getTradeChestConfigManager().msg("<red>Player not found."));
            return;
        }
        plugin.getTradeChestManager().setCustomShopLimit(offline.getUniqueId(), limit);
        player.sendMessage(plugin.getTradeChestConfigManager().msg(
                "<green>Set shop limit for <white><name></white> to <white><limit></white>.",
                Placeholder.unparsed("name", targetName),
                Placeholder.unparsed("limit", String.valueOf(limit))));
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("── TradeChest Help ──").color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true));
        player.sendMessage(helpLine("/shop create",   "Look at a chest, hold item to sell"));
        player.sendMessage(helpLine("/shop remove",   "Look at your shop chest to remove it"));
        player.sendMessage(helpLine("/shop settings", "Manage a shop you own"));
        player.sendMessage(helpLine("/shop shops",    "Browse all your shops"));
        player.sendMessage(helpLine("/shop stats",    "View your sales statistics"));
        if (player.hasPermission("tradechest.admin")) {
            player.sendMessage(helpLine("/shop reload",                   "Reload config and shops"));
            player.sendMessage(helpLine("/shop admin setlimit <player> <n>", "Set custom shop limit"));
        }
    }

    private Component helpLine(String cmd, String desc) {
        return Component.text(cmd).color(NamedTextColor.YELLOW)
                .append(Component.text(" – ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text(desc).color(NamedTextColor.GRAY));
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        if (args.length == 1) {
            var opts = new ArrayList<>(List.of("create", "remove", "settings", "shops", "stats", "help"));
            if (player.hasPermission("tradechest.admin")) { opts.add("reload"); opts.add("admin"); }
            return opts.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) return List.of("setlimit");
        return List.of();
    }
}
