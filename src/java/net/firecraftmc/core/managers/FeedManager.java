package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.Prefixes;
import net.firecraftmc.shared.classes.enums.Rank;
import net.firecraftmc.shared.classes.model.player.FirecraftPlayer;
import net.firecraftmc.shared.command.FirecraftCommand;

public class FeedManager {

    public FeedManager(FirecraftCore plugin) {
        FirecraftCommand feed = new FirecraftCommand("feed", "Feed yourself") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (args.length >= 1) {
                    if (!Rank.isStaff(player.getMainRank())) {
                        player.sendMessage(Prefixes.FEED + Messages.cannotFeedOthers);
                        return;
                    }
                    FirecraftPlayer target = plugin.getPlayerManager().getPlayer(args[0]);
                    if (target == null) {
                        player.sendMessage(Prefixes.FEED + Messages.feedInvalidTarget);
                        return;
                    }
        
                    target.getPlayer().setFoodLevel(20);
                    target.sendMessage(Prefixes.FEED + Messages.beenFed);
                } else {
                    player.getPlayer().setFoodLevel(20);
                    player.sendMessage(Prefixes.FEED + Messages.beenFed);
                }
            }
        };
        feed.addRanks(Rank.ADMINISTRATION).addRanks(Rank.MODERATION).addRanks(Rank.SPECIAL).addRanks(Rank.MEDIA).addRanks(Rank.DONORS);
    }
}
