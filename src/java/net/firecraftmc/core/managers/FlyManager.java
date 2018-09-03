package net.firecraftmc.core.managers;

import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.core.FirecraftCore;

public class FlyManager {

    public FlyManager(FirecraftCore plugin) {
        FirecraftCommand fly = new FirecraftCommand("fly", "Toggles your fly status") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (player.getMainRank().equals(Rank.TRIAL_MOD) || player.getMainRank().equals(Rank.MOD)) {
                    player.sendMessage("<ec>Please use staff mode if you have to fly.");
                    return;
                }
                
                player.setAllowFlight(!player.getAllowFlight());
                player.sendMessage("<nc>You have toggled flight to <nc>" + player.getAllowFlight());
            }
        }.setBaseRank(Rank.VIP);
        
        plugin.getCommandManager().addCommand(fly);
    }
}