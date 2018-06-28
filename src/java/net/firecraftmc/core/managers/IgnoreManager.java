package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.enums.Rank;
import net.firecraftmc.shared.classes.model.player.FirecraftPlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class IgnoreManager implements CommandExecutor {

    private FirecraftCore plugin;
    public IgnoreManager(FirecraftCore plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Messages.onlyPlayers);
            return true;
        }

        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
        if (cmd.getName().equalsIgnoreCase("ignore")) {
            if (!(args.length > 0)) {
                player.sendMessage(Messages.notEnoughArgs);
                return true;
            }

            if (Rank.isStaff(player.getMainRank())) {
                if (!player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                    player.sendMessage("&cStaff members cannot ignored other players.");
                    return true;
                }
            }

            for (String i : args) {
                FirecraftPlayer target = plugin.getPlayerManager().getPlayer(i);
                if (target == null) {
                    player.sendMessage("&cThe name {name} is not valid".replace("{name}", i));
                    continue;
                }

                if (Rank.isStaff(target.getMainRank())) {
                    player.sendMessage("&cYou cannot ignore a staff memnber.");
                    continue;
                }

                player.addIgnored(target.getUniqueId());
                player.sendMessage(Messages.ignoreAction("added", "to", i));
            }
        } else if (cmd.getName().equalsIgnoreCase("unignore")) {
            if (!(args.length > 0)) {
                player.sendMessage(Messages.notEnoughArgs);
                return true;
            }

            for (String i : args) {
                FirecraftPlayer target = plugin.getPlayerManager().getPlayer(i);
                if (target == null) {
                    player.sendMessage("&cThe name {name} is not valid".replace("{name}", i));
                    continue;
                }

                player.removeIgnored(target.getUniqueId());
                player.sendMessage(Messages.ignoreAction("removed", "from", i));
            }
        }
        return true;
    }
}