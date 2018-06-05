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

public class TimeManager implements CommandExecutor {
    private FirecraftCore plugin;
    private final String prefix = "&d&l[Time] ";
    
    public TimeManager(FirecraftCore plugin) {
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
                int time;
                String timeName = "";
                if (cmd.getName().equalsIgnoreCase("time")) {
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
                                player.sendMessage(prefix + Messages.invalidTime);
                                return true;
                            }
                        }
                        if (timeName.equals("")) {
                            world.setTime(time);
                            player.sendMessage(prefix + Messages.timeChange(time + "", world.getName()));
                        } else {
                            world.setTime(time);
                            player.sendMessage(prefix + Messages.timeChange(timeName, world.getName()));
                        }
                    } else {
                        player.sendMessage(Messages.notEnoughArgs);
                        return true;
                    }
                } else if (cmd.getName().equalsIgnoreCase("day")) {
                    world.setTime(1000);
                    player.sendMessage(prefix + Messages.timeChange("day", world.getName()));
                } else if (cmd.getName().equalsIgnoreCase("night")) {
                    world.setTime(14000);
                    player.sendMessage(prefix + Messages.timeChange("night", world.getName()));
                }
            }
        } else {
            sender.sendMessage(Messages.onlyPlayers);
            return true;
        }
        return true;
    }
}