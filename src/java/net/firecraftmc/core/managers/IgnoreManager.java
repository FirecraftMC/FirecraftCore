package net.firecraftmc.core.managers;

import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.util.Messages;
import net.firecraftmc.core.FirecraftCore;

public class IgnoreManager {

    public IgnoreManager(FirecraftCore plugin) {
        FirecraftCommand ignore = new FirecraftCommand("ignore", "Ignore players") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (!(args.length > 0)) {
                    player.sendMessage(Messages.notEnoughArgs);
                    return;
                }
    
                if (Rank.isStaff(player.getMainRank())) {
                    if (!player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                        player.sendMessage("<ec>Staff members cannot ignore other players.");
                        return;
                    }
                }
    
                for (String i : args) {
                    FirecraftPlayer target = plugin.getPlayerManager().getPlayer(i);
                    if (target == null) {
                        player.sendMessage("<ec>The name {name} is not valid".replace("{name}", i));
                        continue;
                    }
        
                    if (Rank.isStaff(target.getMainRank())) {
                        player.sendMessage("<ec>{name} is a staff member, you cannot ignore them.".replace("{name}", i));
                        continue;
                    }
                    
                    if (player.isIgnoring(target.getUniqueId())) {
                        player.sendMessage("<ec>{name} is already on your ignored users list.".replace("{name}", i));
                        continue;
                    }
        
                    player.addIgnored(target.getUniqueId());
                    player.sendMessage(Messages.ignoreAction("added", "to", i));
                }
            }
        };
        ignore.addRanks(Rank.values());
        
        FirecraftCommand unignore = new FirecraftCommand("unignore", "Remove ignored players.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (!(args.length > 0)) {
                    player.sendMessage(Messages.notEnoughArgs);
                    return;
                }
    
                for (String i : args) {
                    FirecraftPlayer target = plugin.getPlayerManager().getPlayer(i);
                    if (target == null) {
                        player.sendMessage("<ec>The name {name} is not valid".replace("{name}", i));
                        continue;
                    }
                    
                    if (!player.isIgnoring(target.getUniqueId())) {
                        player.sendMessage("<ec>{name} is not on your ignored users list.".replace("{name}", i));
                        continue;
                    }
        
                    player.removeIgnored(target.getUniqueId());
                    player.sendMessage(Messages.ignoreAction("removed", "from", i));
                }
            }
        };
        unignore.addRanks(Rank.values());
        
        plugin.getCommandManager().addCommands(ignore, unignore);
    }
}