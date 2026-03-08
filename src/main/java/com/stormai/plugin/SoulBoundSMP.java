package com.stormai.plugin;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Main plugin class – registers commands, listeners and manages the core data.
 */
public class SoulBoundSMP extends JavaPlugin {

    private SoulManager soulManager;

    @Override
    public void onEnable() {
        // Initialize manager
        this.soulManager = new SoulManager(this);
        this.soulManager.loadConfig();

        // Register command and listener
        getCommand("souls").setExecutor(new com.stormai.plugin.Command.SoulCommand(this));
        getServer().getPluginManager().registerEvents(new com.stormai.plugin.Listener.PlayerListener(this), this);

        getLogger().info("SoulBoundSMP enabled!");
    }

    @Override
    public void onDisable() {
        soulManager.saveConfig();
        getLogger().info("SoulBoundSMP disabled!");
    }

    /** Helper to access the SoulManager from other classes */
    public SoulManager getSoulManager() {
        return soulManager;
    }
}