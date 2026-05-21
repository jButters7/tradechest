package com.launcher;

import com.launcher.commands.TradeChestCommand;
import com.launcher.listeners.TradeChestInteractListener;
import com.launcher.listeners.TradeChestPlayerListener;
import com.launcher.listeners.TradeChestProtectionListener;
import com.launcher.managers.TradeChestConfigManager;
import com.launcher.managers.TradeChestManager;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    private static Main instance;

    private TradeChestConfigManager configManager;
    private TradeChestManager shopManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        configManager = new TradeChestConfigManager(this);
        shopManager = new TradeChestManager(this);

        var cmd = new TradeChestCommand(this);
        var command = getCommand("tradechest");
        if (command != null) {
            command.setExecutor(cmd);
            command.setTabCompleter(cmd);
        }

        getServer().getPluginManager().registerEvents(new TradeChestInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new TradeChestProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new TradeChestPlayerListener(this), this);

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
    public TradeChestConfigManager getTradeChestConfigManager() { return configManager; }
    public TradeChestManager getTradeChestManager() { return shopManager; }
}
