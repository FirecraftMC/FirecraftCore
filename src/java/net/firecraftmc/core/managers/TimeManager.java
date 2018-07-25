package net.firecraftmc.core.managers;

import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.util.Messages;
import net.firecraftmc.api.util.Prefixes;
import net.firecraftmc.core.FirecraftCore;
import org.bukkit.World;

public class TimeManager {
    public TimeManager(FirecraftCore plugin) {
        FirecraftCommand time = new FirecraftCommand("time", "Modify the world time.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                World world = player.getPlayer().getWorld();
                int time;
                String timeName = "";
                if (args.length > 0) {
                    if (args[0].equalsIgnoreCase("day") || args[0].equalsIgnoreCase("d")) {
                        time = 1000;
                        timeName = "day";
                    } else if (args[0].equalsIgnoreCase("noon") || args[0].equalsIgnoreCase("no")) {
                        time = 6000;
                        timeName = "noon";
                    } else if (args[0].equalsIgnoreCase("sunset") || args[0].equalsIgnoreCase("s")) {
                        time = 12000;
                        timeName = "sunset";
                    } else if (args[0].equalsIgnoreCase("night") || args[0].equalsIgnoreCase("ni")) {
                        time = 14000;
                        timeName = "night";
                    } else if (args[0].equalsIgnoreCase("midnight") || args[0].equalsIgnoreCase("m")) {
                        time = 18000;
                        timeName = "midnight";
                    } else {
                        try {
                            time = Integer.parseInt(args[0]);
                        } catch (NumberFormatException e) {
                            player.sendMessage(Prefixes.TIME + Messages.invalidTime);
                            return;
                        }
                    }
                    if (timeName.equals("")) {
                        world.setTime(time);
                        player.sendMessage(Prefixes.TIME + Messages.timeChange(time + "", world.getName()));
                    } else {
                        world.setTime(time);
                        player.sendMessage(Prefixes.TIME + Messages.timeChange(timeName, world.getName()));
                    }
                } else {
                    player.sendMessage(Messages.notEnoughArgs);
                }
            }
        }.setBaseRank(Rank.ADMIN);
        
        FirecraftCommand day = new FirecraftCommand("day", "Set the world time to day") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                player.getPlayer().getWorld().setTime(1000);
                player.sendMessage(Prefixes.TIME + Messages.timeChange("day", player.getPlayer().getWorld().getName()));
            }
        }.setBaseRank(Rank.ADMIN);
        
        FirecraftCommand night = new FirecraftCommand("night", "Set the world time to night") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                player.getPlayer().getWorld().setTime(14000);
                player.sendMessage(Prefixes.TIME + Messages.timeChange("night", player.getPlayer().getWorld().getName()));
            }
        }.setBaseRank(Rank.ADMIN);
        
        plugin.getCommandManager().addCommands(time, day, night);
    }
}