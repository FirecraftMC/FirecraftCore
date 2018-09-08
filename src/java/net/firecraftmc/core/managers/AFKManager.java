package net.firecraftmc.core.managers;

import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.toggles.Toggle;
import net.firecraftmc.core.FirecraftCore;

public class AFKManager {

    public AFKManager(FirecraftCore plugin) {
        FirecraftCommand afk = new FirecraftCommand("afk", "Toggle away from keyboard status") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                 player.toggle(Toggle.AFK);
                 if (player.getToggleValue(Toggle.AFK)) {
                     player.getPlayer().setPlayerListName(player.getName() + " §b[AFK]");
                     player.sendMessage("<nc>You have toggled &aon <nc>AFK Status");
                 } else {
                     player.updatePlayerListName();
                     player.sendMessage("<nc>You have toggled &coff <nc>AFK Status");
                 }
            }
        }.setBaseRank(Rank.DEFAULT);
        
        plugin.getCommandManager().addCommand(afk);
    }
}