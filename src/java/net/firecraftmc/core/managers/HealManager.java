package net.firecraftmc.core.managers;

import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.util.Prefixes;
import net.firecraftmc.core.FirecraftCore;

public class HealManager {

    public HealManager(FirecraftCore plugin) {
        FirecraftCommand heal = new FirecraftCommand("heal", "Heal yourself") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (args.length >= 1) {
                    if (!Rank.isStaff(player.getMainRank())) {
                        player.sendMessage(Prefixes.HEAL + "<ec>You are not allowed to heal other players.");
                        return;
                    }
                    FirecraftPlayer target = plugin.getPlayerManager().getPlayer(args[0]);
                    if (target == null) {
                        player.sendMessage(Prefixes.HEAL + "<ec>The name/uuid you provided is not valid.");
                        return;
                    }
    
                    target.getPlayer().setHealth(20);
                    target.sendMessage(Prefixes.HEAL + "<nc>You have been healed.");
                } else {
                    player.getPlayer().setHealth(20);
                    player.sendMessage(Prefixes.HEAL + "<nc>You have been healed.");
                }
            }
        };
        heal.setBaseRank(Rank.EMBER);
    }
}
