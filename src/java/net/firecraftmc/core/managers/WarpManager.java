package net.firecraftmc.core.managers;

import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.interfaces.IWarpManager;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.model.server.Warp;
import net.firecraftmc.api.util.*;
import net.firecraftmc.core.FirecraftCore;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class WarpManager implements IWarpManager {

    private final List<Warp> warps = new ArrayList<>();

    private final File file;
    private final FileConfiguration config;

    public WarpManager(FirecraftCore plugin) {
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
    
        FirecraftCommand setWarp = new FirecraftCommand("setwarp", "Set a warp given a name to your location.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                Warp warp;
                if (args.length == 2) {
                    Rank rank;
                    try {
                        rank = Rank.valueOf(args[1].toUpperCase());
                    } catch (Exception e) {
                        player.sendMessage(Prefixes.WARPS + Messages.invalidRank);
                        return;
                    }
                    warp = new Warp(args[0], player.getLocation(), rank);
                } else {
                    warp = new Warp(args[0], player.getLocation());
                }
                warps.remove(warp);
                warps.add(warp);
                player.sendMessage(Prefixes.WARPS + Messages.setWarp(warp));
            }
        }.setBaseRank(Rank.ADMIN);
    
        FirecraftCommand delWarp = new FirecraftCommand("delwarp", "Removes the specified warp") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                Warp warp = null;
                for (Warp w : warps) {
                    if (w.getName().equalsIgnoreCase(args[0])) {
                        warp = w;
                    }
                }
    
                if (warp == null) {
                    player.sendMessage(Prefixes.WARPS + Messages.warpDoesNotExist);
                    return;
                }
    
                if (warp.getMinimumRank().isHigher(player.getMainRank())) {
                    player.sendMessage(Prefixes.WARPS + Messages.noPermission);
                    return;
                }
    
                warps.remove(warp);
                player.sendMessage(Prefixes.WARPS + Messages.delWarp(warp.getName()));
            }
        }.setBaseRank(Rank.ADMIN);
    
        FirecraftCommand warp = new FirecraftCommand("warp", "Goes to the warp specified") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                Warp warp = null;
                
                if (args.length == 0) {
                    StringBuilder sb = new StringBuilder();
                    if (warps.isEmpty()) {
                        sb.append("none");
                    } else {
                        for (int i=0; i<warps.size(); i++) {
                            Warp w = warps.get(i);
                            if (player.getMainRank().isEqualToOrHigher(w.getMinimumRank())) {
                                sb.append(w.getName());
                                if (i != warps.size()-1) {
                                    sb.append(", ");
                                }
                            }
                        }
                    }
                    
                    player.sendMessage("<nc>The warps that are available to you are <vc>" + sb.toString());
                    return;
                }
                
                for (Warp w : warps) {
                    if (w.getName().equalsIgnoreCase(args[0])) {
                        warp = w;
                    }
                }
    
                if (warp == null) {
                    player.sendMessage(Prefixes.WARPS + Messages.warpDoesNotExist);
                    return;
                }
    
                if (!player.getMainRank().isEqualToOrHigher(warp.getMinimumRank())) {
                    player.sendMessage(Prefixes.WARPS + Messages.noPermission);
                    return;
                }
    
                player.teleport(warp.getLocation());
                player.sendMessage(Prefixes.WARPS + Messages.warpTeleport(warp.getName()));
            }
        }.setBaseRank(Rank.ADMIN);
        
        plugin.getCommandManager().addCommands(setWarp, delWarp, warp);
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
}