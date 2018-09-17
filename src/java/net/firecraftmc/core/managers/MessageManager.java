package net.firecraftmc.core.managers;

import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Channel;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.packets.FPacketPrivateMessage;
import net.firecraftmc.api.toggles.Toggle;
import net.firecraftmc.api.util.*;
import net.firecraftmc.core.FirecraftCore;
import org.bukkit.Bukkit;

public class MessageManager {

    public MessageManager(FirecraftCore plugin) {
        plugin.getSocket().addSocketListener(packet -> {
            if (packet instanceof FPacketPrivateMessage) {
                FPacketPrivateMessage messagePacket = ((FPacketPrivateMessage) packet);
                FirecraftPlayer target = plugin.getPlayerManager().getPlayer(messagePacket.getTarget());
                if (target.getPlayer() != null) {
                    String senderName = plugin.getFCDatabase().getPlayerName(messagePacket.getSender());
                    target.sendMessage(Utils.Chat.formatPrivateMessage(senderName, "You", messagePacket.getMessage()));
                    target.setLastMessage(messagePacket.getSender());
                }
            }
        });
    
        FirecraftCommand message = new FirecraftCommand("message", "Send private messages to other players.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (!(args.length > 0)) {
                    player.sendMessage(Prefixes.MESSAGING + "<ec>You must provide someone to message.");
                    return;
                }
                
                FirecraftPlayer target = plugin.getPlayerManager().getPlayer(args[0]);
                
                if (target.isVanished()) {
                    if (!player.getMainRank().isHigher(target.getMainRank())) {
                        player.sendMessage(Prefixes.MESSAGING + Messages.notOnline);
                        return;
                    }
                }
                
                if (target.isNicked()) { //TODO Possible bug here
                    if (target.getName().equalsIgnoreCase(args[0])) {
                        player.sendMessage(Prefixes.MESSAGING + Messages.notOnline);
                        return;
                    }
                }
                
                if (target.isIgnoring(player.getUniqueId())) {
                    player.sendMessage(Prefixes.MESSAGING + "<ec>You cannot send that player messages because they are ignoring you.");
                    return;
                }
                
                if (target.getToggleValue(Toggle.RECORDING)) {
                    player.sendMessage(Prefixes.MESSAGING + "<ec>That player is recording, they cannot receive messages.");
                    return;
                }
    
                if (!target.getProfile().getToggleValue(Toggle.getToggle("messages"))) {
                    if (!Rank.isStaff(player.getMainRank())) {
                        player.sendMessage("<ec>That player has messages disabled");
                        return;
                    } else {
                        if (!player.getMainRank().isEqualToOrHigher(target.getMainRank())) {
                            player.sendMessage("<ec>That player has messages disabled");
                            return;
                        }
                    }
                }
    
                if (!(args.length > 1)) {
                    player.setChannel(Channel.PRIVATE);
                    player.setLastMessage(target.getUniqueId());
                    player.sendMessage(Prefixes.MESSAGING + "<nc>You opened a chat conversation with " + target.getDisplayName() + "<nc>.");
                    player.sendMessage(Prefixes.MESSAGING + "<nc>Use <vc>/chat global <nc> to leave.");
                    player.getScoreboard().updateScoreboard(player);
                    return;
                }
                
                if (target.getPlayer() == null) {
                    if (!player.getMainRank().isEqualToOrHigher(Rank.PHOENIX)) {
                        player.sendMessage(Prefixes.MESSAGING + "<ec>Messaging across servers is for the" + Rank.PHOENIX.getPrefix() + " <ec>rank or above.");
                        return;
                    }
                    
                    if (!plugin.getFCDatabase().getOnlineStatus(target.getUniqueId())) {
                        player.sendMessage(Prefixes.MESSAGING + Messages.notOnline);
                        return;
                    }
    
                    String message = Utils.getReason(1, args);
                    if (plugin.getFCServer() == null) {
                        player.sendMessage(Prefixes.CHAT + Messages.serverNotSet);
                        return;
                    }
                    FPacketPrivateMessage pMPacket = new FPacketPrivateMessage(plugin.getFCServer().getId(), player.getUniqueId(), target.getUniqueId(), message);
                    plugin.getSocket().sendPacket(pMPacket);
                    player.sendMessage(Utils.Chat.formatPrivateMessage("You", target.getName(), message));
                    player.setLastMessage(target.getUniqueId());
                } else {
                    sendMessages(player, target, args, 1);
                }
            }
        };
        message.addAliases("msg", "whisper", "tell", "pm").setBaseRank(Rank.DEFAULT);
        
        FirecraftCommand reply = new FirecraftCommand("reply", "Reply to a message") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (Bukkit.getPlayer(player.getLastMessage()) == null) {
                    if (plugin.getFCDatabase().getOnlineStatus(player.getLastMessage())) {
                        String message = Utils.getReason(0, args);
                        if (plugin.getFCServer() == null) {
                            player.sendMessage(Prefixes.CHAT + Messages.serverNotSet);
                            return;
                        }
                        FPacketPrivateMessage pMPacket = new FPacketPrivateMessage(plugin.getFCServer().getId(), player.getUniqueId(), player.getLastMessage(), message);
                        plugin.getSocket().sendPacket(pMPacket);
                        player.sendMessage(Utils.Chat.formatPrivateMessage("You", plugin.getFCDatabase().getPlayerName(player.getLastMessage()), message));
                        player.setLastMessage(player.getLastMessage());
                        return;
                    } else {
                        player.sendMessage(Prefixes.MESSAGING + Messages.notOnline);
                        return;
                    }
                }
    
                FirecraftPlayer target = plugin.getPlayerManager().getPlayer(player.getLastMessage());
                sendMessages(player, target, args, 0);
            }
        };
        reply.addAlias("r").setBaseRank(Rank.DEFAULT);
        
        plugin.getCommandManager().addCommands(message, reply);
    }
    
    public void sendMessages(FirecraftPlayer player, FirecraftPlayer target, String[] args, int reasonIndex) {
        if (target.getToggleValue(Toggle.RECORDING)) {
            player.sendMessage(Prefixes.MESSAGING + Messages.recordingNoMessage);
            return;
        }
        String message = Utils.getReason(reasonIndex, args);
        sendMessages(player, target, message);
    }
    
    public void sendMessages(FirecraftPlayer player, FirecraftPlayer target, String message) {
        if (target.isNicked()) {
            player.sendMessage(Utils.Chat.formatPrivateMessage("You", target.getNick().getProfile().getName(), message));
        } else {
            player.sendMessage(Utils.Chat.formatPrivateMessage("You", target.getName(), message));
        }
    
        if (player.isNicked()) {
            target.sendMessage(Utils.Chat.formatPrivateMessage(player.getNick().getProfile().getName(), "You", message));
        } else {
            target.sendMessage(Utils.Chat.formatPrivateMessage(player.getName(), "You", message));
        }
        player.setLastMessage(target.getUniqueId());
        target.setLastMessage(player.getUniqueId());
    }
}