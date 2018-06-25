package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.Prefixes;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.classes.enums.Rank;
import net.firecraftmc.shared.classes.model.player.FirecraftPlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

public class FeedManager implements CommandExecutor {
    private final FirecraftCore plugin;

    public FeedManager(FirecraftCore plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (sender instanceof ConsoleCommandSender) {
            sender.sendMessage(Prefixes.FEED + Messages.consoleNotImplemented);
            return true;
        } else if (sender instanceof Player) {
            FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());

            if (!Utils.checkFirecraftPlayer((Player) sender, player)) return true;
            if (args.length >= 1) {
                if (!Rank.isStaff(player.getMainRank())) {
                    player.sendMessage(Prefixes.FEED + Messages.cannotFeedOthers);
                    return true;
                }
                FirecraftPlayer target = plugin.getPlayerManager().getPlayer(args[0]);
                if (target == null) {
                    player.sendMessage(Prefixes.FEED + Messages.feedInvalidTarget);
                    return true;
                }

                target.getPlayer().setFoodLevel(20);
                target.sendMessage(Prefixes.FEED + Messages.beenFed);
                return true;
            } else {
                if (!player.getMainRank().isEqualToOrHigher(Rank.EMBER)) {
                    player.sendMessage(Prefixes.FEED + Messages.noPermission);
                    return true;
                }

                player.getPlayer().setFoodLevel(20);
                player.sendMessage(Prefixes.FEED + Messages.beenFed);
                return true;
            }
        }
        return false;
    }
}
