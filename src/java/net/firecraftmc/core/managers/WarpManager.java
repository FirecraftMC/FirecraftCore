package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.model.FirecraftPlayer;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.classes.enums.Rank;
import net.firecraftmc.shared.classes.model.Warp;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class WarpManager implements CommandExecutor {

    private FirecraftCore plugin;
    private List<Warp> warps = new ArrayList<>();
    private static final String prefix = "&d&l[Warps] ";

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
                Location location = Utils.getLocationFromString(basePath + ".location");
                Warp warp;
                if (config.contains(basePath + ".minimumrank")) {
                    Rank rank = Rank.valueOf(config.getString(basePath + ".minimumrank"));
                    warp = new Warp(w, location, rank);
                } else warp = new Warp(w, location);
                this.warps.add(warp);
            }
        }
    }

    public void saveWarps() {
        config.set("warps", null);
        try {
            config.save(file);
        } catch (IOException e) {}
        for (Warp warp : warps) {
            config.set("warps." + warp.getName() + ".minimumrank", warp.getMinimumRank().toString());
            config.set("warps." + warp.getName() + ".location", Utils.convertLocationToString(warp.getLocation()));
        }
        try {
            config.save(file);
        } catch (IOException e) {}
    }

    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Messages.onlyPlayers);
            return true;
        }

        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());

        if (!(args.length > 0)) {
            player.sendMessage(prefix + Messages.notEnoughArgs);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("setwarp")) {
            if (player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                if (player.isRecording()) {
                    player.sendMessage(prefix + Messages.recordingNoUse);
                    return true;
                }
                Warp warp;
                if (args.length == 2) {
                    Rank rank;
                    try {
                        rank = Rank.valueOf(args[1].toUpperCase());
                    } catch (Exception e) {
                        player.sendMessage(prefix + Messages.invalidRank);
                        return true;
                    }
                    warp = new Warp(args[0], player.getLocation(), rank);
                } else {
                    warp = new Warp(args[0], player.getLocation());
                }
                this.warps.remove(warp);
                this.warps.add(warp);
                player.sendMessage(prefix + Messages.setWarp(warp));
            } else {
                player.sendMessage(prefix + Messages.noPermission);
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("delwarp")) {
            if (player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                if (player.isRecording()) {
                    player.sendMessage(prefix + Messages.recordingNoUse);
                    return true;
                }
                Warp warp = null;
                for (Warp w : warps) {
                    if (w.getName().equalsIgnoreCase(args[0])) {
                        warp = w;
                    }
                }

                if (warp == null) {
                    player.sendMessage(prefix + Messages.warpDoesNotExist);
                    return true;
                }

                if (warp.getMinimumRank().isHigher(player.getMainRank())) {
                    player.sendMessage(prefix + Messages.noPermission);
                    return true;
                }

                this.warps.remove(warp);
                player.sendMessage(prefix + Messages.delWarp(warp.getName()));
            } else {
                player.sendMessage(prefix + Messages.noPermission);
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("warp")) {
            Warp warp = null;
            for (Warp w : warps) {
                if (w.getName().equalsIgnoreCase(args[0])) {
                    warp = w;
                }
            }

            if (warp == null) {
                player.sendMessage(prefix + Messages.warpDoesNotExist);
                return true;
            }

            if (warp.getMinimumRank().isHigher(player.getMainRank())) {
                player.sendMessage(prefix + Messages.noPermission);
                return true;
            }

            player.teleport(warp.getLocation());
            player.sendMessage(prefix + Messages.warpTeleport(warp.getName()));
        }

        return true;
    }
}