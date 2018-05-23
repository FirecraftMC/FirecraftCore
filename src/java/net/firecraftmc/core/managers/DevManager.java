package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.FirecraftPlayer;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.enums.Rank;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DevManager implements CommandExecutor {
    
    private FirecraftCore plugin;
    
    public DevManager(FirecraftCore plugin) {
        this.plugin = plugin;
    }
    
    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Messages.onlyPlayers);
            return true;
        }
    
        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
        if (!player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
            player.sendMessage(Messages.onlyFirecraftTeam);
            return true;
        }
        
        if (args.length <= 0) {
            player.sendMessage(Messages.noSubCommand);
            return true;
        }
        
        if (args[0].equalsIgnoreCase("testmsg")) {
            StringBuilder msg = new StringBuilder();
            for (int i=1; i<args.length; i++) {
                msg.append(args[i]).append(" ");
            }
            player.sendMessage(msg.toString());
        } else {
            player.sendMessage(Messages.invalidSubCommand);
        }
        
        return true;
    }
}