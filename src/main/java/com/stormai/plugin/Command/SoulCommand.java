package com.stormai.plugin.Command;

import com.stormai.plugin.SoulBoundSMP;
import com.stormai.plugin.Manager.SoulManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles all "/souls" sub‑commands.
 */
public class SoulCommand implements CommandExecutor {

    private final SoulBoundSMP plugin;

    public SoulCommand(SoulBoundSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            // Default: open GUI
            plugin.getSoulManager().openSoulGui(p);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "info":
                p.sendMessage(ChatColor.AQUA + "You currently hold " + plugin.getSoulManager().getSoulCount(p) + " Souls.");
                break;
            case "abilities":
                plugin.getSoulManager().openAbilitiesMenu(p);
                break;
            case "consume":
                plugin.getSoulManager().attemptConsumeSoul(p);
                break;
            case "revive":
                plugin.getSoulManager().attemptRevive(p);
                break;
            default:
                p.sendMessage(ChatColor.RED + "Usage: /souls <info|abilities|consume|revive>");
        }
        return true;
    }
}