package net.firecraftmc.core.managers;

import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.util.Messages;
import net.firecraftmc.core.FirecraftCore;

import java.util.*;

public class ListManager {
    
    public ListManager(FirecraftCore plugin) {
        FirecraftCommand list = new FirecraftCommand("list", "Get a list of all online players.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                TreeMap<Rank, List<String>> onlinePlayers = new TreeMap<>();
                int onlineCount = 0;
                for (FirecraftPlayer fp : plugin.getPlayerManager().getPlayers()) {
                    Rank r = fp.getMainRank();
                    if (fp.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                        if (!player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                            continue;
                        }
                    }
        
                    onlineCount++;
                    onlinePlayers.computeIfAbsent(r, k -> new ArrayList<>());
        
                    onlinePlayers.get(r).add(fp.getNameNoPrefix());
                }
                player.sendMessage(Messages.listHeader(onlineCount));
                for (Map.Entry<Rank, List<String>> entry : onlinePlayers.entrySet()) {
                    if (!entry.getValue().isEmpty()) {
                        String line = generateListLine(entry.getKey(), entry.getValue());
                        if (entry.getKey().equals(Rank.FIRECRAFT_TEAM)) {
                            if (player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                                player.sendMessage(line);
                                continue;
                            } else {
                                continue;
                            }
                        }
                        player.sendMessage(line);
            
                    }
                }
            }
        };
        list.setBaseRank(Rank.DEFAULT);
        
        FirecraftCommand stafflist = new FirecraftCommand("stafflist", "List all online staff.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                Map<String, List<FirecraftPlayer>> onlineStaff = plugin.getFCDatabase().getOnlineStaffMembers();
    
                if (onlineStaff.isEmpty()) {
                    player.sendMessage("&cThere was an issue with getting the list of online staff members.");
                    return;
                }
    
                List<String> displayStrings = new ArrayList<>();
                int serverCount = 0, playerCount = 0;
                for (String server : onlineStaff.keySet()) {
                    serverCount++;
                    String base = " &8- &7" + server + "&7(&f" + onlineStaff.get(server).size() + "&7): ";
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < onlineStaff.get(server).size(); i++) {
                        FirecraftPlayer fp = onlineStaff.get(server).get(i);
                        if (fp.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                            if (!player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                                continue;
                            }
                        }
                        sb.append(fp.getNameNoPrefix());
                        if (i != onlineStaff.get(server).size() - 1) {
                            sb.append("&7, ");
                        }
                        playerCount++;
                    }
                    if (!sb.toString().equals("")) {
                        displayStrings.add(base + sb.toString());
                    }
                }
    
                player.sendMessage(Messages.staffListHeader(playerCount, serverCount));
                for (String ss : displayStrings) {
                    player.sendMessage(ss);
                }
            }
        };
        stafflist.setBaseRank(Rank.TRIAL_MOD);
        
        plugin.getCommandManager().addCommands(list, stafflist);
    }
    
    private String generateListLine(Rank rank, List<String> players) {
        StringBuilder base = new StringBuilder(" &8- &7" + rank.getTeamName() + " (&f" + players.size() + "&7): ");
        for (int i = 0; i < players.size(); i++) {
            String name = players.get(i);
            base.append(name);
            if (i != players.size() - 1) {
                base.append("&7, ");
            }
        }
        return base.toString();
    }
}