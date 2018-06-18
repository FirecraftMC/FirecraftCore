package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.Prefixes;
import net.firecraftmc.shared.classes.model.player.FirecraftPlayer;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.classes.enums.Channel;
import net.firecraftmc.shared.classes.enums.Rank;
import net.firecraftmc.shared.enforcer.punishments.Punishment;
import net.firecraftmc.shared.packets.staffchat.FPStaffChatMessage;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.sql.ResultSet;
import java.util.List;

public class ChatManager implements CommandExecutor, Listener {
    private final FirecraftCore plugin;

    public ChatManager(FirecraftCore plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.plugin = plugin;

        plugin.getSocket().addSocketListener(packet -> {
            if (packet instanceof FPStaffChatMessage) {
                FPStaffChatMessage staffMessage = (FPStaffChatMessage) packet;
                FirecraftPlayer staffMember = plugin.getPlayerManager().getPlayer(staffMessage.getPlayer());
                String format = Utils.Chat.formatStaffMessage(staffMessage.getServer(), staffMember, staffMessage.getMessage());
                plugin.getPlayerManager().getPlayers().forEach(p -> {
                    if (Rank.isStaff(p.getMainRank())) {
                        if (!p.isRecording()) {
                            p.sendMessage(format);
                        }
                    }
                });
            }
        });
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
                    if (player.getInventory().getItemInMainHand() != null && player.getInventory().getItemInMainHand().getType() != Material.AIR) {
                        String itemName = player.getInventory().getItemInMainHand().getItemMeta().getDisplayName();
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
            FPStaffChatMessage msg = new FPStaffChatMessage(plugin.getFirecraftServer(), player.getUniqueId(), e.getMessage());
            plugin.getSocket().sendPacket(msg);
        }
    }

    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (sender instanceof ConsoleCommandSender) {
            sender.sendMessage(Messages.noPermission);
            return true;
        } else if (sender instanceof Player) {
            FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());

            if (!Utils.checkFirecraftPlayer((Player) sender, player)) return true;
            if (!Utils.Command.checkArgCountExact(sender, args, 1)) return true;
            if (Utils.Command.checkCmdAliases(args, 0, "staff", "st", "s")) {
                if (!Rank.isStaff(player.getMainRank())) {
                    player.sendMessage(Prefixes.CHAT + Messages.onlyStaff);
                    return true;
                }

                if (player.isRecording()) {
                    player.sendMessage(Prefixes.CHAT + Messages.recordingNoUse);
                    return true;
                }

                if (player.getChannel().equals(Channel.STAFF)) {
                    player.sendMessage(Prefixes.CHAT + Messages.alreadyInChannel);
                    return true;
                }
                player.setChannel(Channel.STAFF);
                player.sendMessage(Prefixes.CHAT + Messages.channelSwitch(Channel.STAFF));
            } else if (Utils.Command.checkCmdAliases(args, 0, "global", "gl", "g")) {
                if (player.getChannel().equals(Channel.GLOBAL)) {
                    player.sendMessage(Prefixes.CHAT + Messages.alreadyInChannel);
                    return true;
                }
                player.setChannel(Channel.GLOBAL);
                player.sendMessage(Prefixes.CHAT + Messages.channelSwitch(Channel.GLOBAL));
            } else if (cmd.getName().equalsIgnoreCase("global")) {
                if (!(args.length > 0)) {
                    player.sendMessage(Prefixes.CHAT + "<ec>You must provide a message to send.");
                    return true;
                }
                String format = Utils.Chat.formatGlobal(player, Utils.getReason(0, args));
                for (FirecraftPlayer op : plugin.getPlayerManager().getPlayers()) {
                    if (!op.isIgnoring(player.getUniqueId())) {
                        op.sendMessage(format);
                    }
                }
            } else if (cmd.getName().equalsIgnoreCase("staff")) {
                if (!(args.length > 0)) {
                    player.sendMessage(Prefixes.CHAT + "<ec>You must provide a message to send.");
                    return true;
                }
                String message = Utils.getReason(0, args);
                FPStaffChatMessage staffChatMessage = new FPStaffChatMessage(plugin.getFirecraftServer(), player.getUniqueId(), message);
                plugin.getSocket().sendPacket(staffChatMessage);
            } else {
                player.sendMessage(Prefixes.CHAT + Messages.noOtherChannels);
                return true;

            }
        }
        return true;
    }
}