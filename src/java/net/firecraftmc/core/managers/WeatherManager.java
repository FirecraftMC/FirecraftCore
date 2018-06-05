package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.model.FirecraftPlayer;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.enums.Rank;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class WeatherManager implements CommandExecutor {
    private FirecraftCore plugin;
    private final String prefix = "&d&l[Weather] ";
    public WeatherManager(FirecraftCore plugin) {
        this.plugin = plugin;
    }
    
    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (sender instanceof Player) {
            FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
            if (player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                if (player.isRecording()) {
                    player.sendMessage(prefix + Messages.recordingNoUse);
                    return true;
                }
                World world = player.getPlayer().getWorld();
                if (args.length > 0) {
                    if (args[0].equalsIgnoreCase("clear") || args[0].equalsIgnoreCase("c")) {
                        world.setThundering(false);
                        world.setStorm(false);
                        world.setWeatherDuration(Integer.MAX_VALUE);
                        player.sendMessage(prefix + Messages.weatherChange("clear", world.getName()));
                    } else if (args[0].equalsIgnoreCase("storm") || args[0].equalsIgnoreCase("s")) {
                        world.setStorm(true);
                        world.setThundering(true);
                        world.setWeatherDuration(Integer.MAX_VALUE);
                        player.sendMessage(prefix + Messages.weatherChange("storm", world.getName()));
                    } else if (args[0].equalsIgnoreCase("rain") || args[0].equalsIgnoreCase("r")) {
                        world.setStorm(true);
                        world.setThundering(false);
                        world.setWeatherDuration(Integer.MAX_VALUE);
                        player.sendMessage(prefix + Messages.weatherChange("rain", world.getName()));
                    }
                } else {
                    player.sendMessage(Messages.notEnoughArgs);
                    return true;
                }
            } else {
                player.sendMessage(Messages.noPermission);
                return true;
            }
        } else {
            sender.sendMessage(Messages.onlyPlayers);
            return true;
        }
        return true;
    }
}