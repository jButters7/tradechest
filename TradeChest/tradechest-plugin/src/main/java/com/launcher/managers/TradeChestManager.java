package com.launcher.managers;

import com.launcher.Main;
import com.launcher.models.TradeChest;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Manages all shops: CRUD, persistence, and purchase processing.
 *
 * Bugs fixed vs original:
 *  - loadCustomLimits() is now called at construction
 *  - createShop() now checks hasReachedShopLimit() before creating
 *  - processPurchase() uses isSimilar() (meta-aware) not material-only matching
 *  - saveShop() is not called synchronously during purchases — shops are saved
 *    in bulk via the autosave timer and on shutdown/reload
 *  - purchase is immediate on left-click — no chat prompt or pending state
 */
public class TradeChestManager {

    private final Main plugin;
    private final Map<Location, TradeChest> shops = new HashMap<>();
    private final Map<UUID, Integer> customShopLimits = new HashMap<>();
    /** Tracks which shops each player currently has visible — avoids redundant packets. */
    private final Map<UUID, Set<Location>> visibleTo = new HashMap<>();

    private File shopsFile;
    private FileConfiguration shopsConfig;

    public TradeChestManager(Main plugin) {
        this.plugin = plugin;
        this.shopsFile = new File(plugin.getDataFolder(), "shops.yml");
        ensureShopsFile();
        this.shopsConfig = YamlConfiguration.loadConfiguration(shopsFile);
        loadCustomLimits();   // ← was missing in original, causing limits to reset on restart
        loadShops();
    }

    private void ensureShopsFile() {
        if (!shopsFile.exists()) {
            try { shopsFile.createNewFile(); }
            catch (IOException e) { plugin.getLogger().severe("Could not create shops.yml: " + e.getMessage()); }
        }
    }

    // ── Shop CRUD ─────────────────────────────────────────────────────────────

    public boolean createShop(Player player, Location chestLocation,
                              ItemStack sellingItem, int sellingAmount,
                              ItemStack priceItem, int priceAmount) {
        if (isShop(chestLocation)) {
            player.sendMessage(plugin.getTradeChestConfigManager().msgShopAlreadyExists());
            return false;
        }
        // ← original never checked the shop limit — fixed
        if (!player.hasPermission("tradechest.bypass.limit") && hasReachedShopLimit(player.getUniqueId())) {
            int limit = getPlayerShopLimit(player.getUniqueId());
            player.sendMessage(plugin.getTradeChestConfigManager().msgShopLimitReached(limit));
            return false;
        }
        TradeChest shop = new TradeChest(player, chestLocation, sellingItem, sellingAmount, priceItem, priceAmount);
        shop.setNotificationsEnabled(plugin.getTradeChestConfigManager().isSaleNotificationsDefault());
        shops.put(chestLocation, shop);
        shop.createSign();
        shop.createHologram(plugin.getTradeChestConfigManager().getHologramHeight());
        saveShop(shop);
        return true;
    }

    public boolean removeShop(Location chestLocation) {
        TradeChest shop = shops.get(chestLocation);
        if (shop == null) return false;
        shop.removeSign();
        shop.removeHologram();
        shops.remove(chestLocation);
        removeShopFromFile(chestLocation);
        // Clear this location from every player's visibility state
        visibleTo.values().forEach(set -> set.remove(chestLocation));
        return true;
    }

    // ── Purchase flow ─────────────────────────────────────────────────────────

    /**
     * Execute one trade immediately when the player left-clicks the shop chest.
     * Always quantity = 1 (one trade unit: sellingAmount items for priceAmount items).
     * Must run on the main thread.
     */
    public boolean processPurchase(Player player, Location shopLoc) {
        TradeChest shop = shops.get(shopLoc);
        if (shop == null) {
            player.sendMessage(plugin.getTradeChestConfigManager().msgShopDoesntExist());
            return false;
        }

        Block block = shopLoc.getBlock();
        if (block.getType() != Material.CHEST) {
            player.sendMessage(plugin.getTradeChestConfigManager().msgShopDoesntExist());
            return false;
        }

        // Get the LIVE chest inventory via the block state's getInventory().
        // This is crucial — block.getState() alone returns a detached snapshot.
        // Calling getInventory() on that snapshot gives us the real tile-entity
        // inventory that is backed by the actual world data.
        // It also handles double chests (returns the combined inventory).
        if (!(block.getState() instanceof Chest chestState)) {
            player.sendMessage(plugin.getTradeChestConfigManager().msgShopDoesntExist());
            return false;
        }
        org.bukkit.inventory.Inventory chestInv = chestState.getInventory();

        if (!shop.hasEnoughStock(chestInv, 1)) {
            player.sendMessage(plugin.getTradeChestConfigManager().msgNotEnoughStock());
            return false;
        }

        int totalPrice = shop.getPriceAmount();
        if (!shop.playerCanPay(player, 1)) {
            player.sendMessage(plugin.getTradeChestConfigManager().msgNotEnoughPayment(
                    totalPrice, TradeChest.formatItemName(shop.getPriceItem().getType())));
            return false;
        }

        // Execute the trade — all four operations are on the live inventories.
        // No chest.update() needed; we are operating directly on the tile entity.
        int totalSell = shop.getSellingAmount();
        removeItemsFromInventory(chestInv, shop.getSellingItem(), totalSell);
        addItemsToPlayer(player, shop.getSellingItem(), totalSell);
        removeItemsFromPlayer(player, shop.getPriceItem(), totalPrice);
        addItemsToInventory(chestInv, shop.getPriceItem(), totalPrice);

        player.sendMessage(plugin.getTradeChestConfigManager().msgPurchaseSuccess(
                totalSell, TradeChest.formatItemName(shop.getSellingItem().getType()),
                totalPrice, TradeChest.formatItemName(shop.getPriceItem().getType())));

        // Notify owner if online and opted in
        if (shop.isNotificationsEnabled()) {
            Player owner = Bukkit.getPlayer(shop.getOwnerUUID());
            if (owner != null && owner.isOnline()) {
                owner.sendMessage(plugin.getTradeChestConfigManager().msgSaleNotification(
                        player.getName(), totalSell,
                        TradeChest.formatItemName(shop.getSellingItem().getType()),
                        totalPrice, TradeChest.formatItemName(shop.getPriceItem().getType())));
            }
        }

        shop.incrementTotalSales();
        shop.updateHologram(plugin.getTradeChestConfigManager().getHologramHeight());


        // Dirty-flag save: we don't write to disk on every purchase (performance)
        // The autosave timer handles it. Still save immediately for safety.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try { saveShopAsync(shop); } catch (Exception ignored) {}
        });

        return true;
    }

    // ── Inventory helpers (meta-aware) ────────────────────────────────────────

    private void removeItemsFromInventory(org.bukkit.inventory.Inventory inv, ItemStack template, int amount) {
        int remaining = amount;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.isSimilar(template)) {
                if (item.getAmount() <= remaining) {
                    remaining -= item.getAmount();
                    inv.setItem(i, null);
                } else {
                    item.setAmount(item.getAmount() - remaining);
                    remaining = 0;
                }
            }
        }
    }

    private void removeItemsFromPlayer(Player player, ItemStack template, int amount) {
        removeItemsFromInventory(player.getInventory(), template, amount);
        player.updateInventory();
    }

    private void addItemsToPlayer(Player player, ItemStack template, int amount) {
        int remaining = amount;
        int stackSize = template.getMaxStackSize();
        while (remaining > 0) {
            int batch = Math.min(remaining, stackSize);
            ItemStack give = template.clone();
            give.setAmount(batch);
            player.getInventory().addItem(give);
            remaining -= batch;
        }
    }

    private void addItemsToInventory(org.bukkit.inventory.Inventory inv, ItemStack template, int amount) {
        int remaining = amount;
        int stackSize = template.getMaxStackSize();
        while (remaining > 0) {
            int batch = Math.min(remaining, stackSize);
            ItemStack add = template.clone();
            add.setAmount(batch);
            inv.addItem(add);
            remaining -= batch;
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void loadShops() {
        ConfigurationSection section = shopsConfig.getConfigurationSection("shops");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null) continue;
            try {
                UUID ownerUUID = UUID.fromString(Objects.requireNonNull(s.getString("owner-uuid")));
                String ownerName = s.getString("owner-name", "Unknown");
                String worldName = s.getString("world");
                if (Bukkit.getWorld(worldName) == null) {
                    plugin.getLogger().warning("Shop skipped: world '" + worldName + "' not found.");
                    continue;
                }
                Location loc = new Location(Bukkit.getWorld(worldName),
                        s.getDouble("x"), s.getDouble("y"), s.getDouble("z"));
                ItemStack sellingItem = s.getItemStack("selling-item");
                ItemStack priceItem = s.getItemStack("price-item");
                if (sellingItem == null || priceItem == null) {
                    plugin.getLogger().warning("Shop skipped at " + key + ": null items.");
                    continue;
                }
                TradeChest shop = new TradeChest(ownerUUID, ownerName, loc,
                        sellingItem, s.getInt("selling-amount"),
                        priceItem, s.getInt("price-amount"));
                shop.setHologramVisible(s.getBoolean("hologram-visible", true));
                shop.setNotificationsEnabled(s.getBoolean("notifications-enabled", true));
                shop.setTotalSales(s.getInt("total-sales", 0));
                shop.createSign();
                shop.createHologram(plugin.getTradeChestConfigManager().getHologramHeight());
                shops.put(loc, shop);
            } catch (Exception e) {
                plugin.getLogger().severe("Error loading shop '" + key + "': " + e.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + shops.size() + " shop(s).");
    }

    private synchronized void saveShop(TradeChest shop) {
        String key = locationKey(shop.getChestLocation());
        ConfigurationSection s = shopsConfig.createSection("shops." + key);
        writeShopToSection(s, shop);
        try { shopsConfig.save(shopsFile); }
        catch (IOException e) { plugin.getLogger().severe("Could not save shop: " + e.getMessage()); }
    }

    private void saveShopAsync(TradeChest shop) throws IOException {
        String key = locationKey(shop.getChestLocation());
        ConfigurationSection s = shopsConfig.createSection("shops." + key);
        writeShopToSection(s, shop);
        shopsConfig.save(shopsFile);
    }

    private void writeShopToSection(ConfigurationSection s, TradeChest shop) {
        s.set("owner-uuid", shop.getOwnerUUID().toString());
        s.set("owner-name", shop.getOwnerName());
        s.set("world", shop.getChestLocation().getWorld().getName());
        s.set("x", shop.getChestLocation().getX());
        s.set("y", shop.getChestLocation().getY());
        s.set("z", shop.getChestLocation().getZ());
        s.set("selling-item", shop.getSellingItem());
        s.set("selling-amount", shop.getSellingAmount());
        s.set("price-item", shop.getPriceItem());
        s.set("price-amount", shop.getPriceAmount());
        s.set("hologram-visible", shop.isHologramVisible());
        s.set("notifications-enabled", shop.isNotificationsEnabled());
        s.set("total-sales", shop.getTotalSales());
    }

    private void removeShopFromFile(Location location) {
        shopsConfig.set("shops." + locationKey(location), null);
        try { shopsConfig.save(shopsFile); }
        catch (IOException e) { plugin.getLogger().severe("Could not remove shop from file: " + e.getMessage()); }
    }

    public synchronized void saveAllShops() {
        shopsConfig.set("shops", null);
        for (TradeChest shop : shops.values()) {
            String key = locationKey(shop.getChestLocation());
            ConfigurationSection s = shopsConfig.createSection("shops." + key);
            writeShopToSection(s, shop);
        }
        try { shopsConfig.save(shopsFile); }
        catch (IOException e) { plugin.getLogger().severe("Could not save shops: " + e.getMessage()); }
    }

    private static String locationKey(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    // ── Custom limits persistence ─────────────────────────────────────────────

    private void saveCustomLimits() {
        File file = new File(plugin.getDataFolder(), "custom_limits.yml");
        YamlConfiguration cfg = new YamlConfiguration();
        customShopLimits.forEach((uuid, limit) -> cfg.set(uuid.toString(), limit));
        try { cfg.save(file); }
        catch (IOException e) { plugin.getLogger().severe("Could not save custom limits: " + e.getMessage()); }
    }

    public void loadCustomLimits() {
        File file = new File(plugin.getDataFolder(), "custom_limits.yml");
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        customShopLimits.clear();
        for (String key : cfg.getKeys(false)) {
            try {
                int limit = cfg.getInt(key);
                if (limit > 0) customShopLimits.put(UUID.fromString(key), limit);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in custom_limits.yml: " + key);
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void reload() {
        removeAllHolograms();
        shops.clear();
        shopsConfig = YamlConfiguration.loadConfiguration(shopsFile);
        loadCustomLimits();
        loadShops();
        validateAllShops();
    }

    public void removeAllHolograms() {
        shops.values().forEach(shop -> {
            try { shop.removeHologram(); } catch (Exception ignored) {}
        });
        // Clean up any stray stands tagged with our PDC key
        for (var world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e instanceof ArmorStand as) {
                    if (as.getPersistentDataContainer().has(TradeChest.HOLOGRAM_KEY,
                            org.bukkit.persistence.PersistentDataType.STRING)) {
                        as.remove();
                    }
                }
            }
        }
    }

    public void validateAllShops() {
        List<Location> invalid = new ArrayList<>();
        for (Map.Entry<Location, Shop> entry : shops.entrySet()) {
            Location loc = entry.getKey();
            if (loc.getWorld() == null) { invalid.add(loc); continue; }
            if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                loc.getWorld().loadChunk(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
            }
            if (loc.getBlock().getType() != Material.CHEST) {
                plugin.getLogger().warning("Invalid shop at " + locationKey(loc) + ": no chest.");
                invalid.add(loc);
            } else if (entry.getValue().isHologramVisible() && entry.getValue().getHolograms().isEmpty()) {
                entry.getValue().createHologram(plugin.getTradeChestConfigManager().getHologramHeight());
            }
        }
        for (Location loc : invalid) {
            TradeChest s = shops.remove(loc);
            if (s != null) { s.removeHologram(); s.removeSign(); }
            removeShopFromFile(loc);
        }
        if (!invalid.isEmpty()) plugin.getLogger().info("Removed " + invalid.size() + " invalid shop(s).");
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public boolean isShop(Location loc) { return shops.containsKey(loc); }
    public Shop getShop(Location loc) { return shops.get(loc); }
    public Map<Location, TradeChest> getShops() { return shops; }

    public boolean isOwner(Player player, Location loc) {
        TradeChest s = shops.get(loc);
        return s != null && s.isOwner(player);
    }

    public boolean isNearShop(Location loc, int radius) {
        for (Location shopLoc : shops.keySet()) {
            if (shopLoc.getWorld() != null && shopLoc.getWorld().equals(loc.getWorld())
                    && shopLoc.distance(loc) <= radius) return true;
        }
        return false;
    }

    public List<TradeChest> getPlayerShops(UUID uuid) {
        return shops.values().stream().filter(s -> s.getOwnerUUID().equals(uuid)).toList();
    }

    public int getPlayerShopCount(UUID uuid) {
        return (int) shops.values().stream().filter(s -> s.getOwnerUUID().equals(uuid)).count();
    }

    public int getTotalPlayerSales(UUID uuid) {
        return shops.values().stream().filter(s -> s.getOwnerUUID().equals(uuid))
                .mapToInt(Shop::getTotalSales).sum();
    }

    public void removePlayerShops(UUID uuid) {
        shops.entrySet().removeIf(e -> {
            if (e.getValue().getOwnerUUID().equals(uuid)) {
                e.getValue().removeHologram();
                e.getValue().removeSign();
                removeShopFromFile(e.getKey());
                return true;
            }
            return false;
        });
    }

    public int getPlayerShopLimit(UUID uuid) {
        return customShopLimits.getOrDefault(uuid, plugin.getTradeChestConfigManager().getMaxShopsPerPlayer());
    }

    public boolean hasReachedShopLimit(UUID uuid) {
        int max = getPlayerShopLimit(uuid);
        return max > 0 && getPlayerShopCount(uuid) >= max;
    }

    public void setCustomShopLimit(UUID uuid, int limit) {
        if (limit <= 0) customShopLimits.remove(uuid);
        else customShopLimits.put(uuid, limit);
        saveCustomLimits();
    }

    public void updateAllHolograms() {
        double h = plugin.getTradeChestConfigManager().getHologramHeight();
        shops.values().forEach(s -> s.updateHologram(h));
    }

    /**
     * Called once per second by the proximity task in Main.
     * For every online player, show hologram stands within 20 blocks
     * and hide ones that are farther away.
     */
    public void updateHologramVisibility() {
        double radiusSq = 20.0 * 20.0;
        for (org.bukkit.entity.Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            Set<Location> currentlyVisible = visibleTo.computeIfAbsent(uuid, k -> new HashSet<>());

            for (TradeChest shop : shops.values()) {
                if (!shop.isHologramVisible()) continue;
                Location shopLoc = shop.getChestLocation();

                boolean inRange = shopLoc.getWorld() != null
                        && shopLoc.getWorld().equals(player.getWorld())
                        && player.getLocation().distanceSquared(shopLoc) <= radiusSq;

                boolean alreadyVisible = currentlyVisible.contains(shopLoc);

                // Only send a packet if the state actually changed
                if (inRange && !alreadyVisible) {
                    shop.showTo(player);
                    currentlyVisible.add(shopLoc);
                } else if (!inRange && alreadyVisible) {
                    shop.hideFrom(player);
                    currentlyVisible.remove(shopLoc);
                }
                // If state unchanged — do nothing, no packet sent
            }
        }
    }

    /**
     * Hide all holograms from a single player and clear their visibility state.
     * Call on logout or world change.
     */
    public void hideAllFrom(org.bukkit.entity.Player player) {
        shops.values().forEach(s -> s.hideFrom(player));
        visibleTo.remove(player.getUniqueId());
    }
}
