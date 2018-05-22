package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.FirecraftPlayer;
import net.firecraftmc.shared.classes.Home;
import net.firecraftmc.shared.classes.IHomeManager;
import net.firecraftmc.shared.classes.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HomeManager implements TabExecutor, IHomeManager {

    private File file;
    private FileConfiguration config;

    private static final String prefix = "&d&l[Homes] ";

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

    public void saveHomes(FirecraftPlayer player) {
        config.set("homes." + player.getUniqueId().toString(), null);
        try {
            config.save(file);
        } catch (IOException e) {}
        for (Home home : player.getHomes()) {
            config.set("homes." + player.getUniqueId().toString() + "." + home.getName() + ".world", home.getLocation().getWorld().getName());
            config.set("homes." + player.getUniqueId().toString() + "." + home.getName() + ".x", home.getLocation().getX());
            config.set("homes." + player.getUniqueId().toString() + "." + home.getName() + ".y", home.getLocation().getY());
            config.set("homes." + player.getUniqueId().toString() + "." + home.getName() + ".z", home.getLocation().getZ());
            config.set("homes." + player.getUniqueId().toString() + "." + home.getName() + ".yaw", home.getLocation().getYaw());
            config.set("homes." + player.getUniqueId().toString() + "." + home.getName() + ".pitch", home.getLocation().getPitch());
        }
        try {
            config.save(file);
        } catch (IOException e) {}
    }

    public List<Home> loadHomes(UUID uuid) {
        List<Home> homes = new ArrayList<>();
        if (config.contains("homes." + uuid.toString())) {
            for (String h : config.getConfigurationSection("homes." + uuid.toString()).getKeys(false)) {
                String basePath = "homes.{uuid}.{name}".replace("{uuid}", uuid.toString()).replace("{name}", h);
                World world = Bukkit.getWorld(config.getString(basePath + ".world"));
                double x = config.getInt(basePath + ".x");
                double y = config.getInt(basePath + ".y");
                double z = config.getInt(basePath + ".z");
                float yaw = (float) config.getDouble(basePath + ".yaw");
                float pitch = (float) config.getDouble(basePath + ".pitch");
                Home home = new Home(h, new Location(world, x, y, z, yaw, pitch));
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
                player.sendMessage(prefix + Messages.notEnoughArgs);
                return true;
            }

            player.addHome(new Home(args[0], player.getLocation()));
            player.sendMessage(prefix + Messages.setHome(args[0]));
        } else if (cmd.getName().equalsIgnoreCase("delhome")) {
            if (!(args.length > 0)) {
                player.sendMessage(prefix + Messages.notEnoughArgs);
                return true;
            }

            Home home = player.getHome(args[0]);
            if (home == null) {
                player.sendMessage(prefix + Messages.homeNotExist);
                return true;
            }

            player.removeHome(home);
            player.sendMessage(prefix + Messages.delHome(home.getName()));
        } else if (cmd.getName().equalsIgnoreCase("home")) {
            if (args.length == 0) {
                Home home = player.getHome("home");
                if (home == null) {
                    player.sendMessage(prefix + Messages.homeNotExist);
                    return true;
                }
                player.teleport(home.getLocation());
                player.sendMessage(prefix + Messages.homeTeleport(home.getName()));
                return true;
            }

            Home home = player.getHome(args[0]);
            if (home == null) {
                player.sendMessage(prefix + Messages.homeNotExist);
                return true;
            }
            player.teleport(home.getLocation());
            player.sendMessage(prefix + Messages.homeTeleport(home.getName()));
        }

        return true;
    }

    public List<String> onTabComplete(CommandSender sender, Command cmd, String s, String[] args) {
        return null;
    }
}