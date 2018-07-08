package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.Prefixes;
import net.firecraftmc.shared.classes.enums.Rank;
import net.firecraftmc.shared.classes.model.player.FirecraftPlayer;
import net.firecraftmc.shared.command.FirecraftCommand;
import net.firecraftmc.shared.packets.FPacketSocketBroadcast;

public class BroadcastManager {
    public BroadcastManager(FirecraftCore plugin) {
        plugin.getSocket().addSocketListener(packet -> {
            if (packet instanceof FPacketSocketBroadcast) {
                FPacketSocketBroadcast socketBroadcast = ((FPacketSocketBroadcast) packet);
                String message = Messages.socketBroadcast(socketBroadcast.getMessage());
                plugin.getPlayerManager().getPlayers().forEach(p -> {
                    p.sendMessage("");
                    p.sendMessage(message);
                    p.sendMessage("");
                });
            }
        });

        FirecraftCommand broadcast = new FirecraftCommand("broadcast", "Broadcasts a message to all players on the server.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (args.length == 0) {
                    player.sendMessage(Prefixes.BROADCAST + Messages.notEnoughArgs);
                    return;
                }

                StringBuilder sb = new StringBuilder();
                for (String a : args) {
                    sb.append(a).append(" ");
                }

                for (FirecraftPlayer fp : plugin.getPlayerManager().getPlayers()) {
                    fp.sendMessage("");
                    fp.sendMessage(Messages.broadcast(sb.toString()));
                    fp.sendMessage("");
                }
            }
        };
        broadcast.addAlias("bc");
        broadcast.addRanks(Rank.HEAD_ADMIN, Rank.ADMIN);

        FirecraftCommand socketBroadcast = new FirecraftCommand("socketbroadcast", "Broadcast a message to all servers.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (args.length == 0) {
                    player.sendMessage(Prefixes.BROADCAST + Messages.notEnoughArgs);
                    return;
                }

                StringBuilder sb = new StringBuilder();
                for (String a : args) {
                    sb.append(a).append(" ");
                }

                if (plugin.getFCServer() == null) return;
                FPacketSocketBroadcast socketBroadcast = new FPacketSocketBroadcast(plugin.getFCServer().getId(), sb.toString());
                plugin.getSocket().sendPacket(socketBroadcast);
            }
        };
        socketBroadcast.addRanks(Rank.HEAD_ADMIN);
        socketBroadcast.addAlias("sbc");

        plugin.getCommandManager().addCommands(broadcast, socketBroadcast);
    }
}