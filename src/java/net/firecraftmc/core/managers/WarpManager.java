package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.Warp;
import net.firecraftmc.shared.enums.Rank;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class WarpManager implements CommandExecutor {

    private FirecraftCore plugin;
    private List<Warp> warps = new ArrayList<>();

    private File file;
    private FileConfiguration config;

    public WarpManager(FirecraftCore plugin) {
        this.plugin = plugin;

        file = new File(plugin.getDataFolder() + File.separator + "warps.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {}
        }
        config = YamlConfiguration.loadConfiguration(file);

        if (!config.contains("warps")) {
            config.createSection("warps");
            try {
                config.save(file);
            } catch (IOException e) {}
        }

        ConfigurationSection section = config.getConfigurationSection("warps");
        if (section != null && !section.getKeys(false).isEmpty()) {
            for (String w : section.getKeys(false)) {
                String basePath = "warps.{name}".replace("{name}", w);
                World world = Bukkit.getWorld(config.getString(basePath + ".world"));
                double x = config.getInt(basePath + ".x");
                double y = config.getInt(basePath + ".y");
                double z = config.getInt(basePath + ".z");
                float yaw = (float) config.getDouble(basePath + ".yaw");
                float pitch = (float) config.getDouble(basePath + ".pitch");
                Warp warp;
                if (config.contains(basePath + ".minimumrank")) {
                    Rank rank = Rank.valueOf(config.getString(basePath + ".minimumrank"));
                    warp = new Warp(w, new Location(world, x, y, z, yaw, pitch), rank);
                } else warp = new Warp(w, new Location(world, x, y, z, yaw, pitch));
                this.warps.add(warp);
            }
        }
    }

    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        return true;
    }
}