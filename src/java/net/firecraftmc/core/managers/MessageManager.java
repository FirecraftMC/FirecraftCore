package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.model.FirecraftPlayer;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.classes.enums.Rank;
import net.firecraftmc.shared.packets.FPacketPrivateMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MessageManager implements CommandExecutor {

    private FirecraftCore plugin;
    private final String prefix = "&d&l[Messaging] ";

    public MessageManager(FirecraftCore plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Messages.onlyPlayers);
            return true;
        }

        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());

        if (cmd.getName().equalsIgnoreCase("message")) {
            if (!(args.length > 1)) {
                player.sendMessage(prefix + Messages.notEnoughArgs);
                return true;
            }

            FirecraftPlayer target = plugin.getPlayerManager().getPlayer(args[0]);
            if (target == null) {
                for (FirecraftPlayer p : plugin.getPlayerManager().getPlayers()) {
                    if (p.isNicked()) {
                        if (p.getNick().getProfile().getName().equalsIgnoreCase(args[0])) {
                            target = p;
                            break;
                        }
                    }
                }

                if (target == null) {
                    player.sendMessage(prefix + "&cA player with that name could not be found.");
                    return true;
                }
            }

            if (target.getPlayer() == null) {
                for (FirecraftPlayer p : plugin.getPlayerManager().getPlayers()) {
                    if (p.isNicked()) {
                        if (p.getNick().getProfile().getName().equalsIgnoreCase(args[0])) {
                            target = p;
                            break;
                        }
                    }
                }

                if (target.getPlayer() == null) {
                    if (plugin.getFCDatabase().getOnlineStatus(target.getUniqueId())) {
                        if (player.getMainRank().isEqualToOrHigher(Rank.GENERAL)) {
                            if (target.isIgnoring(player.getUniqueId())) {
                                player.sendMessage(prefix + "&c" + args[0] + " is currently ignoring you.");
                                return true;
                            }
                            String message = Utils.getReason(1, args);
                            FPacketPrivateMessage pMPacket = new FPacketPrivateMessage(plugin.getFirecraftServer(), player.getUniqueId(), target.getUniqueId(), message);
                            plugin.getSocket().sendPacket(pMPacket);
                            player.sendMessage(Utils.Chat.formatPrivateMessage("You", target.getName(), message));
                            player.setLastMessage(target.getUniqueId());
                            return true;
                        } else {
                            player.sendMessage(prefix + "&cThat player is not online.");
                            return true;
                        }
                    } else {
                        player.sendMessage(prefix + "&cThat player is not online.");
                        return true;
                    }
                }
            }

            if (target.isNicked()) {
                if (target.getName().equalsIgnoreCase(args[0])) {
                    if (target.getMainRank().isHigher(player.getMainRank())) {
                        player.sendMessage(prefix + "&cThat player is not online.");
                        return true;
                    }
                }
            }

            if (target.isVanished()) {
                if (target.getMainRank().isHigher(player.getMainRank())) {
                    player.sendMessage(prefix + "&cThat player is not online.");
                    return true;
                }
            }

            if (target.isIgnoring(player.getUniqueId())) {
                player.sendMessage(prefix + "&c" + args[0] + " is currently ignoring you.");
                return true;
            }

            sendMessages(player, target, args, 1);
        } else if (cmd.getName().equalsIgnoreCase("reply")) {
            if (!(args.length > 0)) {
                player.sendMessage(prefix + Messages.notEnoughArgs);
                return true;
            }

            if (Bukkit.getPlayer(player.getLastMessage()) == null) {
                if (plugin.getFCDatabase().getOnlineStatus(player.getLastMessage())) {
                    String message = Utils.getReason(0, args);
                    FPacketPrivateMessage pMPacket = new FPacketPrivateMessage(plugin.getFirecraftServer(), player.getUniqueId(), player.getLastMessage(), message);
                    plugin.getSocket().sendPacket(pMPacket);
                    player.sendMessage(Utils.Chat.formatPrivateMessage("You", plugin.getFCDatabase().getPlayerName(player.getLastMessage()), message));
                    player.setLastMessage(player.getLastMessage());
                    return true;
                } else {
                    player.sendMessage(prefix + "&cThat player is not online.");
                    return true;
                }
            }

            FirecraftPlayer target = plugin.getPlayerManager().getPlayer(player.getLastMessage());
            sendMessages(player, target, args, 0);
        }
        return true;
    }

    private void sendMessages(FirecraftPlayer player, FirecraftPlayer target, String[] args, int reasonIndex) {
        String message = Utils.getReason(reasonIndex, args);
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