package com.launcher;

import com.launcher.commands.TradeChestCommand;
import com.launcher.listeners.ChestInteractListener;
import com.launcher.listeners.PlayerJoinListener;
import com.launcher.listeners.ShopProtectionListener;
import com.launcher.managers.ConfigManager;
import com.launcher.managers.ShopManager;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    private static Main instance;

    private ConfigManager configManager;
    private ShopManager shopManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        configManager = new ConfigManager(this);
        shopManager = new ShopManager(this);

        var cmd = new TradeChestCommand(this);
        var command = getCommand("tradechest");
        if (command != null) {
            command.setExecutor(cmd);
            command.setTabCompleter(cmd);
        }

        getServer().getPluginManager().registerEvents(new ChestInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new ShopProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);

        // Validate shops 2 seconds after startup (chunks may not be loaded yet)
        getServer().getScheduler().runTaskLater(this, () -> shopManager.validateAllShops(), 40L);

        // Auto-save shops on a timer if configured
        int interval = configManager.getAutosaveInterval();
        if (interval > 0) {
            long ticks = interval * 20L;
            getServer().getScheduler().runTaskTimerAsynchronously(this,
                    () -> getServer().getScheduler().runTask(this, () -> shopManager.saveAllShops()),
                    ticks, ticks);
        }

        // Proximity task — runs every 20 ticks (1 second), shows/hides hologram
        // armor stands per-player based on distance. Keeps client entity load low.
        getServer().getScheduler().runTaskTimer(this,
                () -> shopManager.updateHologramVisibility(), 40L, 20L);

        getLogger().info("TradeChest v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (shopManager != null) {
            shopManager.saveAllShops();
            shopManager.removeAllHolograms();
        }
        getLogger().info("TradeChest disabled.");
    }

    public void reload() {
        if (shopManager != null) {
            shopManager.saveAllShops();
            shopManager.removeAllHolograms();
        }
        reloadConfig();
        configManager.reload();
        shopManager.reload();
        getLogger().info("TradeChest reloaded.");
    }

    public static Main getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public ShopManager getShopManager() { return shopManager; }
}
