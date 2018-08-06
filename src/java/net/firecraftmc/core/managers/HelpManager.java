package net.firecraftmc.core.managers;

import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.paginator.Paginator;
import net.firecraftmc.api.paginator.PaginatorFactory;
import net.firecraftmc.core.FirecraftCore;

public class HelpManager {
    
    public HelpManager(FirecraftCore plugin) {
        FirecraftCommand help = new FirecraftCommand("help", "Prints out a list of commands that are available to you.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                PaginatorFactory<FirecraftCommand> paginatorFactory = new PaginatorFactory<>();
                paginatorFactory.setMaxElements(10).setHeader("&bHelp page {pagenumber} out of {totalpages}");
                paginatorFactory.setFooter("&bType /help {nextpage} to view the next page.");
                for (FirecraftCommand cmd : plugin.getCommandManager().getCommands()) {
                    if (cmd.canUse(player)) {
                        paginatorFactory.addElement(cmd);
                    }
                }
    
                Paginator<FirecraftCommand> paginator = paginatorFactory.build();
                if (args.length == 0) {
                    paginator.display(player.getPlayer(), 1);
                } else {
                    paginator.display(player.getPlayer(), Integer.parseInt(args[0]));
                }
            }
        }.setBaseRank(Rank.DEFAULT);
        
        plugin.getCommandManager().addCommand(help);
    }
}