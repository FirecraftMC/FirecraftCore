package net.firecraftmc.core.managers;

import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Channel;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.packets.staffchat.FPStaffChatMessage;
import net.firecraftmc.api.punishments.Punishment;
import net.firecraftmc.api.punishments.Punishment.Type;
import net.firecraftmc.api.toggles.Toggle;
import net.firecraftmc.api.util.*;
import net.firecraftmc.api.vanish.VanishSetting;
import net.firecraftmc.core.FirecraftCore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.List;
import java.util.Random;

public class ChatManager implements Listener {
    private final FirecraftCore plugin;
    
    private boolean globalMuted = false;
    private Rank globalMuteMinSpeak = Rank.TRIAL_MOD;

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
                        if (!p.getToggleValue(Toggle.RECORDING)) {
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
                        player.sendMessage(Prefixes.CHAT + "<ec>You must provide a channel to switch to.");
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
                    
                    if (player.getChannel().equals(Channel.PRIVATE)) {
                        player.setLastMessage(null);
                    }
                    
                    player.setChannel(Channel.GLOBAL);
                    player.sendMessage(Prefixes.CHAT + Messages.channelSwitch(Channel.GLOBAL));
                } else if (Utils.Command.checkCmdAliases(args, 0, "private", "p")) {
                    if (player.getChannel().equals(Channel.PRIVATE)) {
                        player.sendMessage(Prefixes.CHAT + Messages.alreadyInChannel);
                        return;
                    }
                    
                    if (player.getLastMessage() == null) {
                        player.sendMessage(Prefixes.CHAT + "<ec>Please message or reply to someone first.");
                        return;
                    }
                    player.setChannel(Channel.PRIVATE);
                    player.sendMessage(Prefixes.CHAT + Messages.channelSwitch(Channel.PRIVATE));
                }
            }
        };
        chat.addAlias("c").setBaseRank(Rank.DEFAULT);

        FirecraftCommand globalShortcut = new FirecraftCommand("global", "Quick access command for global chat.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (!(args.length > 0)) {
                    player.sendMessage(Prefixes.CHAT + "<ec>You must provide a message to send.");
                    return;
                }
                String format = Utils.Chat.formatGlobal(player, Utils.getReason(0, args));
                plugin.getFCDatabase().saveChatMessage(player.getUniqueId(), plugin.getFCServer(), System.currentTimeMillis(), Channel.GLOBAL, Utils.getReason(0, args));
                for (FirecraftPlayer op : plugin.getPlayerManager().getPlayers()) {
                    if (!op.isIgnoring(player.getUniqueId())) {
                        op.sendMessage(format);
                    }
                }
            }
        };
        globalShortcut.addAlias("g").setBaseRank(Rank.DEFAULT);

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
                plugin.getFCDatabase().saveChatMessage(player.getUniqueId(), plugin.getFCServer(), System.currentTimeMillis(), Channel.STAFF, message);
            }
        };
        staffShortcut.addAlias("s").setBaseRank(Rank.TRIAL_MOD);

        FirecraftCommand clearChat = new FirecraftCommand("clearchat", "Clears the chat of everyone but staff members.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                int lines = 150;
                for (Player pl : Bukkit.getServer().getOnlinePlayers()) {
                    FirecraftPlayer fcPl = plugin.getPlayerManager().getPlayer(pl.getUniqueId());
                    if (fcPl.getMainRank().isEqualToOrHigher(Rank.TRIAL_MOD)) {
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
        };
        clearChat.addAlias("cc").setBaseRank(Rank.MOD);
        
        FirecraftCommand globalMute = new FirecraftCommand("globalmute", "Mutes the global chat for everyone below your rank.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (args.length > 1) {
                    if (globalMuted) {
                        if (Utils.Command.checkCmdAliases(args, 0, "change", "c")) {
                            if (player.getMainRank().isEqualToOrHigher(globalMuteMinSpeak)) {
                                if (Utils.Command.checkCmdAliases(args, 1, "minimumrank", "minrank", "mr")) {
                                    if (args.length > 2) {
                                        Rank r = Rank.getRank(args[2]);
                                        if (r == null) {
                                            player.sendMessage("<ec>You provided an invalid rank.");
                                            return ;
                                        }
                                        globalMuteMinSpeak = r;
                                        for (FirecraftPlayer p : plugin.getPlayers()) {
                                            p.sendMessage("");
                                            p.sendMessage("&4&l╔═══════════════════════════════");
                                            p.sendMessage("&4&l║");
                                            p.sendMessage("&4&l║ &d&lGlobal Mute settings changed!");
                                            p.sendMessage("&4&l║");
                                            p.sendMessage("&4&l║ <nc>Those with the rank " + globalMuteMinSpeak.getPrefix() + " <nc>or higher can speak.");
                                            p.sendMessage("&4&l║ <nc>This change was made by " + player.getNameNoPrefix());
                                            p.sendMessage("&4&l║");
                                            p.sendMessage("&4&l╚═══════════════════════════════");
                                            p.sendMessage("");
                                        }
                                        return;
                                    } else {
                                        player.sendMessage("<ec>You must provide a rank to set.");
                                        return;
                                    }
                                }
                            } else {
                                player.sendMessage("<ec>You cannot change the global mute settings.");
                                return;
                            }
                        } else {
                            player.sendMessage("<ec>Unknown sub command.");
                            return;
                        }
                    } else {
                        player.sendMessage("<ec>Global is currently not muted.");
                        return;
                    }
                }
                
                if (globalMuted) {
                    if (player.getMainRank().isEqualToOrHigher(globalMuteMinSpeak)) {
                        globalMuted = false;
                        for (FirecraftPlayer p : plugin.getPlayers()) {
                            p.sendMessage("");
                            p.sendMessage("&4&l╔══════════════════════════");
                            p.sendMessage("&4&l║");
                            p.sendMessage("&4&l║ &a&lGlobal Chat has been unmuted by <vc>" + player.getNameNoPrefix() + "<nc>!");
                            p.sendMessage("&4&l║ <nc>You may once again speak in global chat.");
                            p.sendMessage("&4&l║");
                            p.sendMessage("&4&l╚══════════════════════════");
                            p.sendMessage("");
                        }
                    } else {
                        player.sendMessage("<ec>You do cannot toggle global chat because of your rank.");
                    }
                } else {
                    globalMuted = true;
                    globalMuteMinSpeak = args.length == 0 ? player.getMainRank() : Rank.getRank(args[0]);
    
                    for (FirecraftPlayer p : plugin.getPlayers()) {
                        p.sendMessage("");
                        p.sendMessage("&4&l╔══════════════════════════");
                        p.sendMessage("&4&l║ &c&lGlobal Chat has been muted by <vc>" + player.getNameNoPrefix() + "<nc>!");
                        p.sendMessage("&4&l║");
                        p.sendMessage("&4&l║ <nc>Only players with the rank " + globalMuteMinSpeak.getPrefix() + " <nc> or higher may speak.");
                        p.sendMessage("&4&l║ <nc>This only affects global chat, other channels are not restricted.");
                        p.sendMessage("&4&l║");
                        p.sendMessage("&4&l╚══════════════════════════");
                        p.sendMessage("");
                    }
                }
            }
        }.addAlias("gmute").setBaseRank(Rank.TRIAL_MOD);

        plugin.getCommandManager().addCommands(chat, globalShortcut, staffShortcut, clearChat, globalMute);
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
                player.sendMessage("<nc>You have acknowledged your warning, you can now speak and use commands.");
                plugin.acknowledgeWarn(player.getUniqueId(), player.getName());
                return;
            } else {
                player.sendMessage("<ec>You cannot speak in chat while you have an unacknowledged warning.");
                player.sendMessage("<ec>Type the code &7" + plugin.getAckCode(player.getUniqueId()));
                return;
            }
        }

        List<Punishment> punishments = plugin.getFCDatabase().getPunishments(player.getUniqueId());
        for (Punishment punishment : punishments) {
            if (punishment.isActive()) {
                if (punishment.getType().equals(Punishment.Type.MUTE) || punishment.getType().equals(Punishment.Type.TEMP_MUTE)) {
                    player.sendMessage("");
                    player.sendMessage("&4&l╔═══════════════════════════════");
                    player.sendMessage("&4&l║ &c&lYou are currently muted!");
                    player.sendMessage("&4&l║");
                    if (punishment.getType().equals(Type.TEMP_MUTE))
                        player.sendMessage("&4&l║ <nc>The mute expires in <vc>" + punishment.formatExpireTime());
                    else player.sendMessage("&4&l║ <nc>This mute is <vc>&lPERMANENT");
                    player.sendMessage("&4&l║ <nc>The staff member that muted you is <vc>" + punishment.getPunisherName());
                    player.sendMessage("&4&l║");
                    player.sendMessage("&4&l╚═══════════════════════════════");
                    player.sendMessage("");
                    return;
                } else if (punishment.getType().equals(Punishment.Type.JAIL)) {
                    player.sendMessage(Messages.chatJailed);
                    return;
                }
            }
        }

        if (player.getChannel().equals(Channel.GLOBAL)) {
            if (player.isVanished() && !player.getVanishSettings().getSetting(VanishSetting.CHAT)) {
                player.sendMessage(Prefixes.CHAT + Messages.noTalkGlobal);
                return;
            }
            
            if (globalMuted) {
                if (!player.getMainRank().isEqualToOrHigher(globalMuteMinSpeak)) {
                    player.sendMessage("<ec>You cannot speak because global chat is currently muted.");
                    return;
                }
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
            
            plugin.getFCDatabase().saveChatMessage(player.getUniqueId(), plugin.getFCServer(), System.currentTimeMillis(), Channel.GLOBAL, e.getMessage());
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
            
            plugin.getFCDatabase().saveChatMessage(player.getUniqueId(), plugin.getFCServer(), System.currentTimeMillis(), Channel.STAFF, e.getMessage());
            
            FPStaffChatMessage msg = new FPStaffChatMessage(plugin.getFCServer().getId(), player.getUniqueId(), e.getMessage());
            plugin.getSocket().sendPacket(msg);
        } else if (player.getChannel().equals(Channel.PRIVATE)) {
            FirecraftPlayer target = plugin.getPlayerManager().getPlayer(player.getLastMessage());
            if (target.isVanished()) {
                if (target.getMainRank().isHigher(player.getMainRank())) {
                    player.sendMessage(Prefixes.CHAT + "<ec>That player is no longer online.");
                    player.setLastMessage(null);
                    player.setChannel(Channel.GLOBAL);
                    return;
                }
            }
            String message = e.getMessage();
            plugin.getMessageManager().sendMessages(player, target, message);
            plugin.getFCDatabase().saveChatMessage(player.getUniqueId(), plugin.getFCServer(), System.currentTimeMillis(), Channel.PRIVATE, "To:" + target.getUniqueId().toString() + " " + e.getMessage());
        }
    }
}