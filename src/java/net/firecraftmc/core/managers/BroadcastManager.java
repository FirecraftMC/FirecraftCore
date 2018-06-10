package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.Prefixes;
import net.firecraftmc.shared.classes.model.player.FirecraftPlayer;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.enums.Rank;
import net.firecraftmc.shared.packets.FPacketSocketBroadcast;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BroadcastManager implements CommandExecutor {
    private FirecraftCore plugin;

    public BroadcastManager(FirecraftCore plugin) {
        this.plugin = plugin;
    }


    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Messages.onlyPlayers);
            return true;
        }

        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());

        if (player.isRecording()) {
            player.sendMessage(Prefixes.BROADCAST + Messages.recordingNoUse);
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Prefixes.BROADCAST + Messages.notEnoughArgs);
            return true;
        }

        StringBuilder sb = new StringBuilder();
        for (String a : args) {
            sb.append(a).append(" ");
        }

        if (cmd.getName().equalsIgnoreCase("broadcast")) {
            if (player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                for (FirecraftPlayer fp : plugin.getPlayerManager().getPlayers()) {
                    fp.sendMessage("");
                    fp.sendMessage(Messages.broadcast(sb.toString()));
                    fp.sendMessage("");
                }
            } else {
                player.sendMessage(Prefixes.BROADCAST + Messages.noPermission);
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("socketbroadcast")) {
            if (player.getMainRank().isEqualToOrHigher(Rank.HEAD_ADMIN)) {
                FPacketSocketBroadcast socketBroadcast = new FPacketSocketBroadcast(plugin.getFirecraftServer(), sb.toString());
                plugin.getSocket().sendPacket(socketBroadcast);
            } else {
                player.sendMessage(Prefixes.BROADCAST + Messages.noPermission);
                return true;
            }
        }

        return true;
    }
}
