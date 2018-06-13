package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.Prefixes;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.classes.enums.Rank;
import net.firecraftmc.shared.classes.exceptions.NicknameException;
import net.firecraftmc.shared.classes.model.ActionBar;
import net.firecraftmc.shared.classes.model.Skin;
import net.firecraftmc.shared.classes.model.player.FirecraftPlayer;
import net.firecraftmc.shared.packets.staffchat.FPStaffChatResetNick;
import net.firecraftmc.shared.packets.staffchat.FPStaffChatSetNick;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.ResultSet;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Random;
import java.util.UUID;

public class NickManager implements CommandExecutor {
    private final FirecraftCore plugin;

    public NickManager(FirecraftCore plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (cmd.getName().equalsIgnoreCase("nick")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(Messages.onlyPlayers);
                return true;
            }

            FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
            if (!Utils.checkFirecraftPlayer((Player) sender, player)) return true;
            if (!Utils.Command.checkArgCountExact(sender, args, 1)) return true;

            if (player.isRecording()) {
                player.sendMessage(Prefixes.NICKNAME + Messages.recordingNoUse);
                return true;
            }

            if (!(player.hasRank(Rank.VIP, Rank.FAMOUS) || player.getMainRank().isEqualToOrHigher(Rank.TRIAL_ADMIN))) {
                player.sendMessage(Prefixes.NICKNAME + Messages.noPermission);
                return true;
            }

            FirecraftPlayer nickname = plugin.getPlayerManager().getPlayer(args[0]);
            if (nickname == null) {
                player.sendMessage(Prefixes.NICKNAME + "<ec>That nickname could not be found.");
                return true;
            }

            Skin skin = plugin.getFCDatabase().getSkin(nickname.getUniqueId());
            if (skin == null || skin.getName() == null || skin.getSignature() == null || skin.getValue() == null) {
                player.sendMessage(Prefixes.NICKNAME + "<ec>There as an error getting the skin information for that player.");
                return true;
            }

            nickname.setSkin(skin);

            try {
                player.setNick(plugin, nickname);
            } catch (NicknameException e) {
                player.sendMessage(Prefixes.NICKNAME + "<ec>There was an error setting your nickname.");
            }

            player.setActionBar(new ActionBar(Messages.actionBar_Nicked));
            FPStaffChatSetNick setNick = new FPStaffChatSetNick(plugin.getFirecraftServer(), player.getUniqueId(), nickname.getName());
            plugin.getSocket().sendPacket(setNick);
            player.sendMessage(Prefixes.NICKNAME + "<nc>You have set your nickname to <vc>" + nickname.getName());
            new BukkitRunnable() {
                public void run() {
                    player.updatePlayerListName();
                }
            }.runTaskLater(plugin, 20L);
        } else if (cmd.getName().equalsIgnoreCase("nickrandom")) {
            if (sender instanceof Player) {
                FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
                if (!(player.getMainRank().equals(Rank.VIP) || player.getMainRank().isEqualToOrHigher(Rank.MODERATOR))) {
                    player.sendMessage(Prefixes.NICKNAME + Messages.noPermission);
                    return true;
                }

                if (player.isRecording()) {
                    player.sendMessage(Prefixes.NICKNAME + Messages.recordingNoUse);
                    return true;
                }

                LinkedList<FirecraftPlayer> possibleNicks = new LinkedList<>();
                ResultSet set = plugin.getFCDatabase().querySQL("SELECT * FROM `playerdata` WHERE `mainrank` <> 'FIRECRAFT_TEAM' AND `mainrank` <> 'HEAD_ADMIN' AND `mainrank` <> 'ADMIN'  AND `mainrank` <> 'TRIAL_ADMIN' AND `mainrank` <> 'MOD' AND `mainrank` <> 'HELPER'  AND `mainrank` <> 'QUALITY_ASSURANCE'  AND `mainrank` <> 'BUILD_TEAM'  AND `mainrank` <> 'VIP'  AND `mainrank` <> 'FAMOUS';");
                try {
                    while (set.next()) {
                        possibleNicks.add(plugin.getFCDatabase().getPlayer(UUID.fromString(set.getString("uniqueid"))));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (possibleNicks.isEmpty()) {
                    player.sendMessage(Prefixes.NICKNAME + "<ec>Could not find any names that could be used.");
                    return true;
                }

                Collections.shuffle(possibleNicks);
                int index = new Random().nextInt(possibleNicks.size());
                FirecraftPlayer nickname = possibleNicks.get(index);
                Skin skin = plugin.getFCDatabase().getSkin(nickname.getUniqueId());
                if (skin == null || skin.getName() == null || skin.getSignature() == null || skin.getValue() == null) {
                    player.sendMessage(Prefixes.NICKNAME + "<ec>There as an error getting the skin information for that player.");
                    return true;
                }

                nickname.setSkin(skin);

                try {
                    player.setNick(plugin, nickname);
                } catch (NicknameException e) {
                    player.sendMessage(Prefixes.NICKNAME + "<ec>There was an error setting your nickname.");
                }

                player.setActionBar(new ActionBar(Messages.actionBar_Nicked));
                FPStaffChatSetNick setNick = new FPStaffChatSetNick(plugin.getFirecraftServer(), player.getUniqueId(), nickname.getName());
                plugin.getSocket().sendPacket(setNick);
                player.sendMessage(Prefixes.NICKNAME + "<nc>You have randomly set your nickname to <vc>" + nickname.getName());
                new BukkitRunnable() {
                    public void run() {
                        player.updatePlayerListName();
                    }
                }.runTaskLater(plugin, 20L);
            } else {
                sender.sendMessage(Prefixes.NICKNAME + Messages.onlyPlayers);
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("unnick")) {
            if (sender instanceof ConsoleCommandSender) {
                sender.sendMessage(Messages.onlyPlayers);
                return true;
            }

            FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
            if (player.getNick() == null) {
                player.sendMessage(Prefixes.NICKNAME + Messages.notSettingNick);
                return true;
            }

            try {
                Skin skin = plugin.getFCDatabase().getSkin(player.getUniqueId());
                player.setSkin(skin);
                player.resetNick(plugin);
            } catch (NicknameException e) {
                player.sendMessage(Prefixes.NICKNAME + Messages.resetNickError);
                return true;
            }

            FPStaffChatResetNick resetNick = new FPStaffChatResetNick(plugin.getFirecraftServer(), player.getUniqueId());
            plugin.getSocket().sendPacket(resetNick);
            player.sendMessage(Prefixes.NICKNAME + "<nc>You have reset your nickname.");
            new BukkitRunnable() {
                public void run() {
                    player.updatePlayerListName();
                }
            }.runTaskLater(plugin, 20L);
        }
        return true;
    }
}