package net.firecraftmc.core.managers;

import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.util.Prefixes;
import net.firecraftmc.core.FirecraftCore;

public class FeedManager {

    public FeedManager(FirecraftCore plugin) {
        FirecraftCommand feed = new FirecraftCommand("feed", "Feed yourself") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (args.length >= 1) {
                    if (!Rank.isStaff(player.getMainRank())) {
                        player.sendMessage(Prefixes.FEED + "<ec>You are not allowed to feed other players.");
                        return;
                    }
                    FirecraftPlayer target = plugin.getPlayerManager().getPlayer(args[0]);
                    if (target == null) {
                        player.sendMessage(Prefixes.FEED + "<ec><ec>The name/uuid you provided is invalid.");
                        return;
                    }
        
                    target.getPlayer().setFoodLevel(20);
                    target.sendMessage(Prefixes.FEED + "<nc>You have been fed!");
                } else {
                    player.getPlayer().setFoodLevel(20);
                    player.sendMessage(Prefixes.FEED + "<nc>You have been fed!s");
                }
            }
        };
        feed.setBaseRank(Rank.EMBER);
    }
}
