package net.firecraftmc.core.managers;

import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.interfaces.IHomeManager;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.model.player.Home;
import net.firecraftmc.api.util.*;
import net.firecraftmc.core.FirecraftCore;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class HomeManager implements IHomeManager {

    private final File file;
    private final FileConfiguration config;

    public HomeManager(FirecraftCore plugin) {
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
    
        FirecraftCommand setHome = new FirecraftCommand("sethome", "Set a home to your current location given a name") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (!(args.length > 0)) {
                    player.sendMessage(Prefixes.HOMES + Messages.notEnoughArgs);
                    return;
                }
    
                player.addHome(new Home(args[0], player.getLocation()));
                player.sendMessage(Prefixes.HOMES + Messages.setHome(args[0]));
            }
        };
        setHome.addRanks(Rank.values());
    
        FirecraftCommand delHome = new FirecraftCommand("delhome", "Deletes a home given the name") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (!(args.length > 0)) {
                    player.sendMessage(Prefixes.HOMES + Messages.notEnoughArgs);
                    return;
                }
    
                Home home = player.getHome(args[0]);
                if (home == null) {
                    player.sendMessage(Prefixes.HOMES + Messages.homeNotExist);
                    return;
                }
    
                player.removeHome(home);
                player.sendMessage(Prefixes.HOMES + Messages.delHome(home.getName()));
            }
        };
        delHome.addRanks(Rank.values());
    
        FirecraftCommand home = new FirecraftCommand("home", "Teleport to a home given a name.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (args.length == 0) {
                    player.sendMessage("<nc>Here are your current homes: <vc>" + player.getHomeListAsString());
                    return;
                }
    
                if (args[0].contains(":")) {
                    if (!player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                        player.sendMessage(Prefixes.HOMES + "<ec>You cannot teleport to other player's homes.");
                        return;
                    }
        
                    String[] arr = args[0].split(":");
                    FirecraftPlayer target = plugin.getPlayerManager().getPlayer(arr[0]);
                    if (target == null) {
                        player.sendMessage(Prefixes.HOMES + "<ec>Could not find a player with that name.");
                        return;
                    }
                    
                    if (args.length == 1) {
                        player.sendMessage(Prefixes.HOMES + "<nc>Here is <vc>" + target.getName() + "'s <nc>homes: <vc>" + target.getHomeListAsString());
                        return;
                    }
                    
                    Home targetHome = target.getHome(arr[1]);
                    if (targetHome == null) {
                        player.sendMessage(Prefixes.HOMES + "<ec>That player does not have a home by that name.");
                        return;
                    }
        
                    player.teleport(targetHome.getLocation());
                    player.sendMessage(Prefixes.HOMES + "<nc>You teleported to <vc>" + target.getName() + "<nc>'s home named <vc>" + targetHome.getName());
                    return;
                }
    
                Home home = player.getHome(args[0]);
                if (home == null) {
                    player.sendMessage(Prefixes.HOMES + Messages.homeNotExist);
                    return;
                }
                player.teleport(home.getLocation());
                player.sendMessage(Prefixes.HOMES + Messages.homeTeleport(home.getName()));
            }
        };
        home.addRanks(Rank.values());
        
        plugin.getCommandManager().addCommands(delHome, setHome, home);
    }

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
}