package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.Prefixes;
import net.firecraftmc.shared.classes.enums.Rank;
import net.firecraftmc.shared.classes.model.player.FirecraftPlayer;
import net.firecraftmc.shared.command.FirecraftCommand;
import org.bukkit.World;

public class WeatherManager {
    public WeatherManager(FirecraftCore plugin) {
        FirecraftCommand weather = new FirecraftCommand("weather", "Modify the weather of the current world.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                World world = player.getPlayer().getWorld();
                if (args.length > 0) {
                    if (args[0].equalsIgnoreCase("clear") || args[0].equalsIgnoreCase("c")) {
                        world.setThundering(false);
                        world.setStorm(false);
                        world.setWeatherDuration(Integer.MAX_VALUE);
                        player.sendMessage(Prefixes.WEATHER + Messages.weatherChange("clear", world.getName()));
                    } else if (args[0].equalsIgnoreCase("storm") || args[0].equalsIgnoreCase("s")) {
                        world.setStorm(true);
                        world.setThundering(true);
                        world.setWeatherDuration(Integer.MAX_VALUE);
                        player.sendMessage(Prefixes.WEATHER + Messages.weatherChange("storm", world.getName()));
                    } else if (args[0].equalsIgnoreCase("rain") || args[0].equalsIgnoreCase("r")) {
                        world.setStorm(true);
                        world.setThundering(false);
                        world.setWeatherDuration(Integer.MAX_VALUE);
                        player.sendMessage(Prefixes.WEATHER + Messages.weatherChange("rain", world.getName()));
                    }
                } else {
                    player.sendMessage(Messages.notEnoughArgs);
                }
            }
        }.setBaseRank(Rank.ADMIN);
        
        plugin.getCommandManager().addCommand(weather);
    }
}