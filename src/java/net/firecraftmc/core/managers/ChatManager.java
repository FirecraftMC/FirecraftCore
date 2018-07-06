package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.Prefixes;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.classes.enums.Channel;
import net.firecraftmc.shared.classes.enums.Rank;
import net.firecraftmc.shared.classes.model.player.FirecraftPlayer;
import net.firecraftmc.shared.command.FirecraftCommand;
import net.firecraftmc.shared.packets.staffchat.FPStaffChatMessage;
import net.firecraftmc.shared.punishments.Punishment;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.List;
import java.util.Random;

public class ChatManager implements Listener {
    private final FirecraftCore plugin;

    public ChatManager(FirecraftCore plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.plugin = plugin;

        plugin.getSocket().addSocketListener(packet -> {
            if (packet instanceof FPStaffChatMessage) {
                FPStaffChatMessage staffMessage = (FPStaffChatMessage) packet;
                FirecraftPlayer staffMember = plugin.getPlayerManager().getPlayer(staffMessage.getPlayer());
                String format = Utils.Chat.formatStaffMessage(plugin.getServerManager().getServer(staffMessage.getServerId()), staffMember, staffMessage.getMessage());
                plugin.getPlayerManager().getPlayers().forEach(p -> {
                    if (Rank.isStaff(p.getMainRank())) {
                        if (!p.isRecording()) {
                            p.sendMessage(format);
                        }
                    }
                });
            }
        });

        FirecraftCommand chat = new FirecraftCommand("chat", "The command for chat channels.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (Utils.Command.checkCmdAliases(args, 0, "staff", "st", "s")) {
                    if (args.length != 1) {
                        player.sendMessage(Prefixes.CHAT + "§cYou must provide a channel to switch to.");
                    }
                    if (!Rank.isStaff(player.getMainRank())) {
                        player.sendMessage(Prefixes.CHAT + Messages.onlyStaff);
                        return;
                    }

                    if (player.getChannel().equals(Channel.STAFF)) {
                        player.sendMessage(Prefixes.CHAT + Messages.alreadyInChannel);
                        return;
                    }
                    player.setChannel(Channel.STAFF);
                    player.sendMessage(Prefixes.CHAT + Messages.channelSwitch(Channel.STAFF));
                } else if (Utils.Command.checkCmdAliases(args, 0, "global", "gl", "g")) {
                    if (player.getChannel().equals(Channel.GLOBAL)) {
                        player.sendMessage(Prefixes.CHAT + Messages.alreadyInChannel);
                        return;
                    }
                    player.setChannel(Channel.GLOBAL);
                    player.sendMessage(Prefixes.CHAT + Messages.channelSwitch(Channel.GLOBAL));
                }
            }

            public void executeConsole(ConsoleCommandSender sender, String[] args) {
                sender.sendMessage(Messages.onlyPlayers);
            }
        };
        chat.addAlias("c");

        FirecraftCommand globalShortcut = new FirecraftCommand("global", "Quick access command for global chat.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (!(args.length > 0)) {
                    player.sendMessage(Prefixes.CHAT + "<ec>You must provide a message to send.");
                    return;
                }
                String format = Utils.Chat.formatGlobal(player, Utils.getReason(0, args));
                for (FirecraftPlayer op : plugin.getPlayerManager().getPlayers()) {
                    if (!op.isIgnoring(player.getUniqueId())) {
                        op.sendMessage(format);
                    }
                }
            }

            public void executeConsole(ConsoleCommandSender sender, String[] args) {
                sender.sendMessage(Messages.onlyPlayers);
            }
        };
        globalShortcut.addAlias("g");

        FirecraftCommand staffShortcut = new FirecraftCommand("staff", "Quick access command for staff chat") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (!(args.length > 0)) {
                    player.sendMessage(Prefixes.CHAT + "<ec>You must provide a message to send.");
                    return;
                }
                String message = Utils.getReason(0, args);

                if (plugin.getFCServer() == null) {
                    player.sendMessage(Prefixes.CHAT + Messages.serverNotSet);
                    return;
                }
                FPStaffChatMessage staffChatMessage = new FPStaffChatMessage(plugin.getFCServer().getId(), player.getUniqueId(), message);
                plugin.getSocket().sendPacket(staffChatMessage);
            }

            @Override
            public void executeConsole(ConsoleCommandSender sender, String[] args) {
                sender.sendMessage(Messages.onlyPlayers);
            }
        };
        staffShortcut.addAlias("s");
        staffShortcut.addRanks(Rank.FIRECRAFT_TEAM, Rank.HEAD_ADMIN, Rank.ADMIN, Rank.TRIAL_ADMIN, Rank.MODERATOR, Rank.HELPER);

        FirecraftCommand clearChat = new FirecraftCommand("clearchat", "Clears the chat of everyone but staff members.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                int lines = 150;
                for (Player pl : Bukkit.getServer().getOnlinePlayers()) {
                    FirecraftPlayer fcPl = plugin.getPlayerManager().getPlayer(pl.getUniqueId());
                    if (fcPl.getMainRank().isEqualToOrHigher(Rank.HELPER)) {
                        fcPl.sendMessage(Prefixes.CHAT + Messages.chatCleared);
                    } else {
                        for (int x = 0; x < lines; x++) {
                            Random rand = new Random();
                            int spaces = rand.nextInt(15);
                            StringBuilder line = new StringBuilder(" ");
                            for (int y = 0; y < spaces; y++) {
                                line.append(" ");
                            }
                            fcPl.sendMessage(line.toString());
                        }
                        fcPl.sendMessage(Prefixes.CHAT + Messages.chatCleared);
                    }
                }
            }

            public void executeConsole(ConsoleCommandSender sender, String[] args) {
                sender.sendMessage(Messages.noPermission);
            }
        };
        clearChat.addAlias("cc");
        clearChat.addRanks(Rank.FIRECRAFT_TEAM, Rank.HEAD_ADMIN, Rank.ADMIN, Rank.TRIAL_ADMIN, Rank.MODERATOR);

        plugin.getCommandManager().addCommands(chat, globalShortcut, staffShortcut, clearChat);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChat(AsyncPlayerChatEvent e) {
        e.setCancelled(true);
        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(e.getPlayer().getUniqueId());
        if (player == null) {
            e.getPlayer().sendMessage(Prefixes.CHAT + Messages.chatNoData);
            return;
        }

        if (!plugin.isWarnAcknowledged(player.getUniqueId())) {
            if (e.getMessage().equals(plugin.getAckCode(player.getUniqueId()))) {
                player.sendMessage(Messages.acknowledgeWarning);
                plugin.acknowledgeWarn(player.getUniqueId(), player.getName());
                return;
            } else {
                player.sendMessage(Messages.chatUnAckWarning);
                return;
            }
        }

        List<Punishment> punishments = plugin.getFCDatabase().getPunishments(player.getUniqueId());
        for (Punishment punishment : punishments) {
            if (punishment.isActive()) {
                if (punishment.getType().equals(Punishment.Type.MUTE) || punishment.getType().equals(Punishment.Type.TEMP_MUTE)) {
                    player.sendMessage(Messages.chatMuted);
                    return;
                } else if (punishment.getType().equals(Punishment.Type.JAIL)) {
                    player.sendMessage(Messages.chatJailed);
                    return;
                }
            }
        }

        if (player.getChannel().equals(Channel.GLOBAL)) {
            if (player.isVanished() && !player.getVanishInfo().canChat()) {
                player.sendMessage(Prefixes.CHAT + Messages.noTalkGlobal);
                return;
            }
            String format = Utils.Chat.formatGlobal(player, e.getMessage());
            if (player.getMainRank().isEqualToOrHigher(Rank.INFERNO)) {
                if (format.toLowerCase().contains("[item]")) {
                    if (player.getInventory().getItemInHand() != null && player.getInventory().getItemInHand().getType() != Material.AIR) {
                        String itemName = player.getInventory().getItemInHand().getItemMeta().getDisplayName();
                        format = format.replace("[item]", itemName);
                    }
                }
            }
            for (FirecraftPlayer op : plugin.getPlayerManager().getPlayers()) {
                if (!op.isIgnoring(player.getUniqueId())) {
                    op.sendMessage(format);
                }
            }
        } else if (player.getChannel().equals(Channel.STAFF)) {
            if (plugin.getFCServer() == null) {
                player.sendMessage(Prefixes.CHAT + Messages.serverNotSet);
                return;
            }
            FPStaffChatMessage msg = new FPStaffChatMessage(plugin.getFCServer().getId(), player.getUniqueId(), e.getMessage());
            plugin.getSocket().sendPacket(msg);
        }
    }
}