package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.enums.Rank;
import net.firecraftmc.shared.classes.model.player.FirecraftPlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class ListManager implements CommandExecutor {

    private FirecraftCore plugin;
    public ListManager(FirecraftCore plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Messages.onlyPlayers);
            return true;
        }

        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());

        if (cmd.getName().equalsIgnoreCase("list")) {
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
                if (onlinePlayers.get(r) == null) {
                    onlinePlayers.put(r, new ArrayList<>());
                }

                onlinePlayers.get(r).add(fp.getNameNoPrefix());
            }
            player.sendMessage(Messages.listHeader(onlineCount));
            for (Map.Entry<Rank, List<String>> entry : onlinePlayers.entrySet()) {
                if (entry.getValue().size() != 0) {
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
        }else if (cmd.getName().equalsIgnoreCase("stafflist")) {
            if (!player.getMainRank().isEqualToOrHigher(Rank.HELPER)) {
                player.sendMessage(Messages.noPermission);
                return true;
            }

            HashMap<String, List<FirecraftPlayer>> onlineStaff = plugin.getFCDatabase().getOnlineStaffMembers();

            if (onlineStaff.isEmpty()) {
                player.sendMessage("&cThere was an issue with getting the list of online staff members.");
                return true;
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

        return true;
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