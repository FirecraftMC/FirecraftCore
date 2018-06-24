package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.Prefixes;
import net.firecraftmc.shared.classes.enums.Rank;
import net.firecraftmc.shared.classes.model.player.FirecraftPlayer;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.classes.interfaces.IHomeManager;
import net.firecraftmc.shared.classes.model.player.Home;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HomeManager implements IHomeManager {

    private File file;
    private FileConfiguration config;

    private FirecraftCore plugin;

    public HomeManager(FirecraftCore plugin) {
        this.plugin = plugin;

        this.file = new File(plugin.getDataFolder() + File.separator + "homes.yml");
        if (!this.file.exists()) {
            try {
                this.file.createNewFile();
            } catch (IOException e) {
            }
        }

        config = YamlConfiguration.loadConfiguration(file);
        if (!config.contains("homes")) {
            config.createSection("homes");
        }
    }

    /**
     * Saves all homes given the player
     * @param player The player to save the homes of.
     */
    public void saveHomes(FirecraftPlayer player) {
        config.set("homes." + player.getUniqueId().toString(), null);
        try {
            config.save(file);
        } catch (IOException e) {}
        for (Home home : player.getHomes()) {
            config.set("homes." + player.getUniqueId().toString() + "." + home.getName(), Utils.convertLocationToString(home.getLocation()));
        }
        try {
            config.save(file);
        } catch (IOException e) {}
    }

    /**
     * Loads the homes given the UUID of the player
     * @param uuid The uuid of the player to load the homes of
     * @return The list of homes that the Player has set
     */
    public List<Home> loadHomes(UUID uuid) {
        List<Home> homes = new ArrayList<>();
        if (config.contains("homes." + uuid.toString())) {
            for (String h : config.getConfigurationSection("homes." + uuid.toString()).getKeys(false)) {
                String basePath = "homes.{uuid}.{name}".replace("{uuid}", uuid.toString()).replace("{name}", h);
                Home home = new Home(h, Utils.getLocationFromString(config.getString(basePath)));
                homes.add(home);
            }
        }

        return homes;
    }

    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Messages.onlyPlayers);
            return true;
        }

        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
        if (cmd.getName().equalsIgnoreCase("sethome")) {
            if (!(args.length > 0)) {
                player.sendMessage(Prefixes.HOMES + Messages.notEnoughArgs);
                return true;
            }

            player.addHome(new Home(args[0], player.getLocation()));
            player.sendMessage(Prefixes.HOMES + Messages.setHome(args[0]));
        } else if (cmd.getName().equalsIgnoreCase("delhome")) {
            if (!(args.length > 0)) {
                player.sendMessage(Prefixes.HOMES + Messages.notEnoughArgs);
                return true;
            }

            Home home = player.getHome(args[0]);
            if (home == null) {
                player.sendMessage(Prefixes.HOMES + Messages.homeNotExist);
                return true;
            }

            player.removeHome(home);
            player.sendMessage(Prefixes.HOMES + Messages.delHome(home.getName()));
        } else if (cmd.getName().equalsIgnoreCase("home")) {
            if (args.length == 0) {
                Home home = player.getHome("home");
                if (home == null) {
                    player.sendMessage(Prefixes.HOMES + Messages.homeNotExist);
                    return true;
                }
                player.teleport(home.getLocation());
                player.sendMessage(Prefixes.HOMES + Messages.homeTeleport(home.getName()));
                return true;
            }

            if (args[0].contains(":")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                    player.sendMessage(Prefixes.HOMES + "<ec>You cannot teleport to other player's homes.");
                    return true;
                }

                String[] arr = args[0].split(":");
                FirecraftPlayer target = plugin.getPlayerManager().getPlayer(arr[0]);
                if (target == null) {
                    player.sendMessage(Prefixes.HOMES + "<ec>Could not find a player with that name.");
                    return true;
                }
                Home targetHome = target.getHome(arr[1]);
                if (targetHome == null) {
                    player.sendMessage(Prefixes.HOMES + "<ec>That player does not have a home by that name.");
                    return true;
                }

                player.teleport(targetHome.getLocation());
                player.sendMessage(Prefixes.HOMES + "<nc>You teleported to <vc>" + target.getName() + "<nc>'s home named <vc>" + targetHome.getName());
                return true;
            }

            Home home = player.getHome(args[0]);
            if (home == null) {
                player.sendMessage(Prefixes.HOMES + Messages.homeNotExist);
                return true;
            }
            player.teleport(home.getLocation());
            player.sendMessage(Prefixes.HOMES + Messages.homeTeleport(home.getName()));
        }

        return true;
    }
}