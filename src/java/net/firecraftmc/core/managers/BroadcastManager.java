package net.firecraftmc.core.managers;

import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.packets.FPacketSocketBroadcast;
import net.firecraftmc.api.util.Messages;
import net.firecraftmc.api.util.Prefixes;
import net.firecraftmc.core.FirecraftCore;

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
        broadcast.addAlias("bc").setBaseRank(Rank.ADMIN);

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
        socketBroadcast.addAlias("sbc").setBaseRank(Rank.HEAD_ADMIN);

        plugin.getCommandManager().addCommands(broadcast, socketBroadcast);
    }
}