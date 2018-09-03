package net.firecraftmc.core.managers;

import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.exceptions.NicknameException;
import net.firecraftmc.api.model.player.*;
import net.firecraftmc.api.packets.staffchat.FPStaffChatResetNick;
import net.firecraftmc.api.packets.staffchat.FPStaffChatSetNick;
import net.firecraftmc.api.util.*;
import net.firecraftmc.core.FirecraftCore;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.ResultSet;
import java.util.*;

public class NickManager {
    public NickManager(FirecraftCore plugin) {
        plugin.getSocket().addSocketListener(packet -> {
            if (packet instanceof FPStaffChatSetNick) {
                FPStaffChatSetNick setNick = ((FPStaffChatSetNick) packet);
                FirecraftPlayer staffMember = plugin.getPlayerManager().getPlayer(setNick.getPlayer());
                String format = Utils.Chat.formatSetNick(plugin.getServerManager().getServer(packet.getServerId()), staffMember, setNick.getProfile());
                Utils.Chat.sendStaffChatMessage(plugin.getPlayerManager().getPlayers(), staffMember, format);
            } else if (packet instanceof FPStaffChatResetNick) {
                FPStaffChatResetNick resetNick = ((FPStaffChatResetNick) packet);
                FirecraftPlayer staffMember = plugin.getPlayerManager().getPlayer(resetNick.getPlayer());
                String format = Utils.Chat.formatResetNick(plugin.getServerManager().getServer(packet.getServerId()), staffMember);
                Utils.Chat.sendStaffChatMessage(plugin.getPlayerManager().getPlayers(), staffMember, format);
            }
        });
    
        FirecraftCommand nick = new FirecraftCommand("nick", "Set your nickname and skin to another player.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                FirecraftPlayer nickname = plugin.getPlayerManager().getPlayer(args[0]);
                if (nickname == null) {
                    player.sendMessage(Prefixes.NICKNAME + "<ec>That nickname could not be found.");
                    return;
                }
    
                Skin skin = plugin.getFCDatabase().getSkin(nickname.getUniqueId());
                if (skin == null || skin.getName() == null || skin.getSignature() == null || skin.getValue() == null) {
                    player.sendMessage(Prefixes.NICKNAME + "<ec>There as an error getting the skin information for that player.");
                    return;
                }
    
                Rank rank;
                if (args.length > 1) {
                    rank = Rank.getRank(args[1]);
                    if (rank == null) {
                        player.sendMessage(Prefixes.NICKNAME + "<ec>The rank you provided was invalid, using the rank of the player.");
                        rank = nickname.getMainRank();
                    }
                } else {
                    rank = nickname.getMainRank();
                }
    
                if (Rank.isStaff(rank) || rank.equals(Rank.BUILD_TEAM)) {
                    if (!player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                        player.sendMessage(Prefixes.NICKNAME + "<ec>You cannot use a staff rank.");
                        return;
                    }
                }
    
                nickname.setSkin(skin);
    
                try {
                    player.setNick(plugin, nickname, rank);
                    plugin.getFCDatabase().updateNickname(player);
                } catch (NicknameException e) {
                    player.sendMessage(Prefixes.NICKNAME + "<ec>There was an error setting your nickname.");
                }
    
                player.setActionBar(new ActionBar(Messages.actionBar_Nicked));
                player.sendMessage(Prefixes.NICKNAME + "<nc>You have set your nickname to <vc>" + nickname.getName());
                new BukkitRunnable() {
                    public void run() {
                        player.updatePlayerListName();
                    }
                }.runTaskLater(plugin, 20L);
                if (plugin.getFCServer() == null) {
                    player.sendMessage(Prefixes.CHAT + Messages.serverNotSet);
                    return;
                }
                FPStaffChatSetNick setNick = new FPStaffChatSetNick(plugin.getFCServer().getId(), player.getUniqueId(), nickname.getName());
                plugin.getSocket().sendPacket(setNick);
            }
        };
        nick.setBaseRank(Rank.FAMOUS).removeRanks(Rank.BUILD_TEAM, Rank.TRIAL_MOD, Rank.MOD);
        
        FirecraftCommand unnick = new FirecraftCommand("unnick", "Remove your nickname.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (player.getNick() == null) {
                    player.sendMessage(Prefixes.NICKNAME + "<ec>You do not have a nickname set.");
                    return;
                }
    
                try {
                    Skin skin = plugin.getFCDatabase().getSkin(player.getUniqueId());
                    player.setSkin(skin);
                    player.resetNick(plugin);
                } catch (NicknameException e) {
                    player.sendMessage(Prefixes.NICKNAME + Messages.resetNickError);
                    return;
                }
                player.sendMessage(Prefixes.NICKNAME + "<nc>You have reset your nickname.");
                new BukkitRunnable() {
                    public void run() {
                        player.updatePlayerListName();
                    }
                }.runTaskLater(plugin, 20L);
    
                if (plugin.getFCServer() == null) {
                    player.sendMessage(Prefixes.CHAT + Messages.serverNotSet);
                    return;
                }
                FPStaffChatResetNick resetNick = new FPStaffChatResetNick(plugin.getFCServer().getId(), player.getUniqueId());
                plugin.getSocket().sendPacket(resetNick);
            }
        };
        unnick.addAliases("resetnick", "nickreset").setBaseRank(Rank.FAMOUS).removeRanks(Rank.BUILD_TEAM, Rank.TRIAL_MOD, Rank.MOD);
        
        FirecraftCommand nickrandom = new FirecraftCommand("nickrandom", "Get a random nick that is not a staff rank.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                LinkedList<FirecraftPlayer> possibleNicks = new LinkedList<>();
                ResultSet set = plugin.getFCDatabase().querySQL("SELECT * FROM `playerdata` WHERE `mainrank` <> 'FIRECRAFT_TEAM' AND `mainrank` <> 'HEAD_ADMIN' AND `mainrank` <> 'ADMIN'  AND `mainrank` <> 'TRIAL_ADMIN' AND `mainrank` <> 'MOD' AND `mainrank` <> 'TRIAL_MOD'  AND `mainrank` <> 'QUALITY_ASSURANCE'  AND `mainrank` <> 'BUILD_TEAM'  AND `mainrank` <> 'VIP'  AND `mainrank` <> 'FAMOUS' AND `online`='false';");
                try {
                    while (set.next()) {
                        possibleNicks.add(plugin.getFCDatabase().getPlayer(UUID.fromString(set.getString("uniqueid"))));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
    
                if (possibleNicks.isEmpty()) {
                    player.sendMessage(Prefixes.NICKNAME + "<ec>Could not find any names that could be used.");
                    return;
                }
    
                Collections.shuffle(possibleNicks);
                int index = new Random().nextInt(possibleNicks.size());
                FirecraftPlayer nickname = possibleNicks.get(index);
                Skin skin = plugin.getFCDatabase().getSkin(nickname.getUniqueId());
                if (skin == null || skin.getName() == null || skin.getSignature() == null || skin.getValue() == null) {
                    player.sendMessage(Prefixes.NICKNAME + "<ec>There as an error getting the skin information for that player.");
                    return;
                }
    
                Rank rank;
                if (args.length > 0) {
                    rank = Rank.getRank(args[0]);
                    if (rank == null) {
                        player.sendMessage(Prefixes.NICKNAME + "<ec>The rank you provided was invalid, using the rank of the player.");
                        rank = nickname.getMainRank();
                    }
        
                    if (Rank.isStaff(rank) || rank.equals(Rank.BUILD_TEAM)) {
                        player.sendMessage(Prefixes.NICKNAME + "<ec>You cannot use a staff rank.");
                        return;
                    }
                } else {
                    rank = nickname.getMainRank();
                }
    
                nickname.setSkin(skin);
    
                try {
                    player.setNick(plugin, nickname, rank);
                    plugin.getFCDatabase().updateNickname(player);
                } catch (NicknameException e) {
                    player.sendMessage(Prefixes.NICKNAME + "<ec>There was an error setting your nickname.");
                }
    
                player.setActionBar(new ActionBar(Messages.actionBar_Nicked));
                FPStaffChatSetNick setNick = new FPStaffChatSetNick(plugin.getFCServer().getId(), player.getUniqueId(), nickname.getName());
                plugin.getSocket().sendPacket(setNick);
                player.sendMessage(Prefixes.NICKNAME + "<nc>You have randomly set your nickname to <vc>" + nickname.getName());
                new BukkitRunnable() {
                    public void run() {
                        player.updatePlayerListName();
                    }
                }.runTaskLater(plugin, 20L);
            }
        };
        nickrandom.setBaseRank(Rank.FAMOUS).removeRanks(Rank.BUILD_TEAM, Rank.TRIAL_MOD, Rank.MOD);
        
        plugin.getCommandManager().addCommands(nick, unnick, nickrandom);
    }
}