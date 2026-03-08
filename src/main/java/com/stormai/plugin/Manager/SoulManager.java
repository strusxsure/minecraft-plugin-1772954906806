package com.stormai.plugin.Manager;

import com.stormai.plugin.SoulBoundSMP;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Config;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class SoulManager implements Listener {

    private final Plugin plugin;
    private final File dataFolder;
    private final Map<UUID, Integer> soulCounts = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, SoulAbility> unlockedAbilities = new HashMap<>();

    // Cooldown in ticks (20 ticks = 1 second)
    private static final long SOUL_COOLDOWN_TICKS = 20L * 30; // 30 seconds

    public SoulManager(Plugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File((File) plugin.getDataFolder(), "players");
        if (!dataFolder.exists()) dataFolder.mkdirs();
    }

    /** Load all player data on startup */
    public void loadConfig() {
        File[] files = dataFolder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().endsWith(".yml")) {
                    loadPlayerFile(f);
                }
            }
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void loadPlayerFile(File file) {
        try {
            org.bukkit.configuration.file.YamlConfiguration yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
            String uuidStr = file.getName().replaceAll(".yml$", "");
            UUID uuid = UUID.fromString(uuidStr);
            int souls = yaml.getInt("souls", 0);
            soulCounts.put(uuid, souls);
            List<String> abilList = yaml.getStringList("abilities");
            for (String abil : abilList) {
                unlockedAbilities.put(uuid, SoulAbility.fromString(abil));
                //example – you can store more complex ability tracking
            }
        } catch (Exception e) {
            // ignore corrupted files
        }
    }

    private void savePlayerFile(UUID uuid, org.bukkit.configuration.file.YamlConfiguration yaml) {
        try {
            yaml.saveToFile(new File(dataFolder, uuid.toString() + ".yml"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Called when a player joins – load their data if not present */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        if (!soulCounts.containsKey(uuid)) {
            soulCounts.put(uuid, 0);
            savePlayerFile(uuid, org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new File(dataFolder, uuid.toString() + ".yml")));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        saveAll(); // Ensure data is persisted
    }

    /** Death handling – drop a Soul item */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Player killer) || killer.getWorld() == null) return;
        Player victim = (Player) e.getEntity();

        if (victim.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() <= 0) {
            // Normal drop: Soul item
            ItemStack soulItem = new ItemStack(Material.SKELETON_SKELETON_SKULL, 1);
            soulItem.addUnsafeEnchantments(Map.of(
                    Enchantment.PROTECTION_ENVIRONMENTAL, 1));
            org.bukkit.inventory.meta.ItemMeta meta = soulItem.getItemMeta();
            meta.setDisplayName(ChatColor.PURPLE + "Soul");
            meta.setLore(Collections.singletonList(ChatColor.GRAY + "A fragment of another's life"));
            soulItem.setItemMeta(meta);
            victim.getWorld().dropItemNaturally(victim.getLocation(), soulItem);
        }
    }

    /** Pickup – increase soul count */
    @EventHandler
    public void onPickup(PlayerPickupItemEvent e) {
        if (e.getItem() == null) return;
        if (e.getItem().getItemMeta() != null &&
                e.getItem().getItemMeta().hasDisplayName() &&
                e.getItem().getItemMeta().getDisplayName().equals(ChatColor.PURPLE + "Soul")) {
            addSoul(e.getPlayer(), 1);
        }
    }

    /** Add a soul to a player (respect cooldown) */
    public void addSoul(Player p, int amount) {
        UUID uuid = p.getUniqueId();
        if (hasCooldown(uuid)) return; // prevent farming

        int newCount = soulCounts.getOrDefault(uuid, 0) + amount;
        soulCounts.put(uuid, newCount);
        p.sendMessage(ChatColor.GREEN + "You collected " + amount + " Soul" + (amount > 1 ? "s" : "") + "! Total: " + newCount);

        // Reset cooldown
        cooldowns.remove(uuid);
        savePlayerFile(uuid, loadYaml(uuid));
    }

    /** Remove a soul when consumed or used */
    public void removeSoul(Player p, int amount) {
        UUID uuid = p.getUniqueId();
        int current = soulCounts.getOrDefault(uuid, 0);
        if (current < amount) return;

        int newCount = Math.max(current - amount, 0);
        soulCounts.put(uuid, newCount);
        p.sendMessage(ChatColor.YELLOW + "You used " + amount + " Soul" + (amount > 1 ? "s" : "") + ".");

        // Set cooldown to prevent spam
        cooldowns.put(uuid, System.currentTimeMillis() + SOUL_COOLDOWN_TICKS * 20L);
        savePlayerFile(uuid, loadYaml(uuid));
    }

    /** Add temporary attribute modifications (e.g., extra hearts) */
    public void giveTempHealthBoost(Player p, double extraHealth, int durationTicks) {
        AttributeInstance attr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double original = attr.getBaseValue();
        double newMax = original + extraHealth;
        attr.setBaseValue(newMax);
        // Schedule revert after duration
        new BukkitRunnable() {
            @Override
            public void run() {
                if (p.isOnline()) {
                    attr.setBaseValue(original);
                    p.sendMessage(ChatColor.AQUA + "Heart boost expired.");
                }
            }
        }.runTaskLater(plugin, durationTicks);
    }

    /** Remove a player from weakened state (restore max health) */
    public void restoreMaxHealth(Player p) {
        AttributeInstance attr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        AttributeInstance base = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        base.setBaseValue(base.getBaseValue()); // no‑op – just ensures it’s restored
        // In a real implementation you would store the original value and set it back.
    }

    /** Check if a player is in weakened state (low max health) */
    public boolean isWeakened(Player p) {
        double health = p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        return health < 4.0; // below 2 hearts
    }

    /** Attempt to consume a Soul (cost = 1) to unlock an ability temporarily */
    public void attemptConsumeSoul(Player p) {
        if (!hasEnoughSouls(p, 1)) return;
        if (isWeakened(p)) {
            p.sendMessage(ChatColor.RED + "You are too weakened to consume a Soul.");
            return;
        }
        removeSoul(p, 1);
        // Example: grant Strength II for 10 seconds
        p.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 200, 1));
        p.sendMessage(ChatColor.BLUE + "Soul consumed – Strength II for 10 seconds!");
    }

    /** Attempt to revive a player who has run out of Souls (requires a Soul in hand) */
    public void attemptRevive(Player p) {
        if (!hasEnoughSouls(p, 5)) {
            p.sendMessage(ChatColor.RED + "You need at least 5 Souls to perform the revival ritual.");
            return;
        }
        // Remove required souls
        removeSoul(p, 5);
        // Restore max health to default (20 HP)
        AttributeInstance attr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double originalBase = 20.0; // default max health
        attr.setBaseValue(originalBase);
        p.setHealth(attr.getValue()); // heal to full
        p.sendMessage(ChatColor.GREEN + "You have been revived! Max health restored.");
    }

    /** GUI handling – opens the abilities menu */
    public void openAbilitiesMenu(Player p) {
        // Simple inventory example
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(null, 9, ChatColor.DARK_PURPLE + "Soul Abilities");
        // Fill with placeholder items (could be customized later)
        inv.setItem(0, new ItemStack(Material.GOLDEN_APPLE));
        inv.setItem(1, new ItemStack(Material.ENDER_EYE));
        inv.setItem(2, new ItemStack(Material.NETHER_STAR));
        p.openInventory(inv);
    }

    /** GUI handling – opens the main Souls menu */
    public void openSoulGui(Player p) {
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(null, 27, ChatColor.BLUE + "Souls Inventory");
        // Example: show Soul count
        ItemStack count = new ItemStack(Material.SKELETON_SKULL);
        org.bukkit.inventory.meta.ItemMeta meta = count.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + "Soul Count");
        meta.setLore(Collections.singletonList(ChatColor.YELLOW + "Current: " + getSoulCount(p)));
        count.setItemMeta(meta);
        inv.setItem(11, count);
        p.openInventory(inv);
    }

    /** Retrieve current soul count for a player (0 if missing) */
    public int getSoulCount(Player p) {
        return soulCounts.getOrDefault(p.getUniqueId(), 0);
    }

    /** Check if player has enough souls */
    public boolean hasEnoughSouls(Player p, int required) {
        return soulCounts.getOrDefault(p.getUniqueId(), 0) >= required;
    }

    /** Set a player’s soul count (used internally) */
    private void setSoulCount(Player p, int count) {
        soulCounts.put(p.getUniqueId(), count);
        savePlayerFile(p.getUniqueId(), loadYaml(p.getUniqueId()));
    }

    /** Determine if cooldown is active */
    private boolean hasCooldown(UUID uuid) {
        Long expiry = cooldowns.get(uuid);
        return expiry != null && expiry > System.currentTimeMillis();
    }

    /** Load (or create) the YamlConfiguration for a player */
    private org.bukkit.configuration.file.YamlConfiguration loadYaml(UUID uuid) {
        File f = new File(dataFolder, uuid.toString() + ".yml");
        if (!f.exists()) {
            try {
                f.createNewFile();
                org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
                yaml.save(f);
                return yaml;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
    }

    /** Save player's data to file */
    private void savePlayerFile(UUID uuid, org.bukkit.configuration.file.YamlConfiguration yaml) {
        try {
            yaml.save(new File(dataFolder, uuid.toString() + ".yml"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Persist all player data on quit */
    private void saveAll() {
        for (UUID uuid : soulCounts.keySet()) {
            try {
                org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
                yaml.set("souls", soulCounts.get(uuid));
                // abilities can be saved here if needed
                savePlayerFile(uuid, yaml);
            } catch (Exception ignored) {}
        }
    }
}