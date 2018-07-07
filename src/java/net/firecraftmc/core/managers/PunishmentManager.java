package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.FirecraftMC;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.Prefixes;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.classes.enums.Rank;
import net.firecraftmc.shared.classes.model.player.FirecraftPlayer;
import net.firecraftmc.shared.classes.model.server.FirecraftServer;
import net.firecraftmc.shared.packets.FPacketAcknowledgeWarning;
import net.firecraftmc.shared.packets.FPacketPunish;
import net.firecraftmc.shared.packets.FPacketPunishRemove;
import net.firecraftmc.shared.punishments.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

public class PunishmentManager implements CommandExecutor, Listener {
    private FirecraftCore plugin;

    public PunishmentManager(FirecraftCore plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        plugin.getSocket().addSocketListener(packet -> {
            if (packet instanceof FPacketPunish)
                Utils.Socket.handlePunish(packet, plugin.getFCDatabase(), plugin.getPlayerManager().getPlayers());
            else if (packet instanceof FPacketPunishRemove)
                Utils.Socket.handleRemovePunish(packet, plugin.getFCDatabase(), plugin.getPlayerManager().getPlayers());
            else if (packet instanceof FPacketAcknowledgeWarning) {
                String format = Utils.Chat.formatAckWarning(plugin.getServerManager().getServer(packet.getServerId()).getName(), ((FPacketAcknowledgeWarning) packet).getWarnedName());
                plugin.getPlayerManager().getPlayers().forEach(p -> p.sendMessage(format));
            }
        });
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        UUID uuid = e.getUniqueId();

        List<Punishment> punishments = plugin.getFCDatabase().getPunishments(uuid);
        for (Punishment punishment : punishments) {
            if (punishment.isActive()) {
                if (punishment.getType().equals(Punishment.Type.TEMP_BAN)) {
                    long expire = punishment.getDate();
                    String expireDiff = Utils.Time.formatTime(expire - System.currentTimeMillis());
                    e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Utils.color(Messages.banMessage(punishment.getPunisherName(), punishment.getReason(), expireDiff, punishment.getId())));
                } else if (punishment.getType().equals(Punishment.Type.BAN)) {
                    e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Utils.color(Messages.banMessage(punishment.getPunisherName(), punishment.getReason(), "Permanent", punishment.getId())));
                }
            }
        }
    }

    @EventHandler
    public void onCommandPreProcess(PlayerCommandPreprocessEvent e) {
        FirecraftPlayer player = plugin.getFCDatabase().getPlayer(e.getPlayer().getUniqueId());
        ResultSet jailSet = plugin.getFCDatabase().querySQL("SELECT * FROM `punishments` WHERE `target`='{uuid}' AND `active`='true' AND `type`='JAIL';".replace("{uuid}", player.getUniqueId().toString()));
        try {
            if (jailSet.next()) {
                player.sendMessage(Messages.jailedNoCmds);
                e.setCancelled(true);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        ResultSet warnSet = plugin.getFCDatabase().querySQL("SELECT * FROM `punishments` WHERE `target`='{uuid}' AND `acknowledged`='false' AND `type`='WARN';".replace("{uuid}", player.getUniqueId().toString()));
        try {
            if (warnSet.next()) {
                player.sendMessage(Messages.unAckWarnNoCmds);
                e.setCancelled(true);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        List<Punishment> punishments = plugin.getFCDatabase().getPunishments(player.getUniqueId());
        for (Punishment punishment : punishments) {
            if (punishment.getType().equals(Punishment.Type.JAIL)) ;
        }
    }

    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (sender instanceof Player) {
            FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
            if (player.isRecording()) {
                player.sendMessage(Prefixes.ENFORCER + Messages.recordingNoUse);
                return true;
            }

            if (cmd.getName().equalsIgnoreCase("setjail")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                    player.sendMessage(Prefixes.ENFORCER + Messages.noPermission);
                    return true;
                }

                plugin.setJailLocation(player.getLocation());
                player.sendMessage(Prefixes.ENFORCER + Messages.setJail);
                return true;
            }

            if (!(args.length > 0)) {
                player.sendMessage(Prefixes.ENFORCER + "&cYou must provide a name or uuid to punish.");
                return true;
            }

            UUID uuid;
            try {
                uuid = UUID.fromString(args[0]);
            } catch (Exception e) {
                uuid = Utils.Mojang.getUUIDFromName(args[0]);
            }

            if (uuid == null) {
                player.sendMessage(Prefixes.ENFORCER + Messages.punishInvalidTarget);
                return true;
            }

            FirecraftPlayer t = plugin.getPlayerManager().getPlayer(uuid);
            if (t == null) {
                t = plugin.getFCDatabase().getPlayer(uuid);
                if (t == null) {
                    player.sendMessage(Prefixes.ENFORCER + Messages.profileNotFound);
                    return true;
                }
            }

            if (t.getMainRank().isEqualToOrHigher(player.getMainRank())) {
                if (!player.getUniqueId().equals(FirecraftMC.firestar311)) {
                    player.sendMessage(Prefixes.ENFORCER + Messages.noPunishRank);
                    return true;
                }
            }

            if (cmd.getName().equalsIgnoreCase("unban") || cmd.getName().equalsIgnoreCase("unmute") || cmd.getName().equalsIgnoreCase("unjail")) {
                ResultSet set = plugin.getFCDatabase().querySQL("SELECT * FROM `punishments` WHERE `target`='{target}' AND `active`='true' AND (`type`='BAN' OR `type`='TEMP_BAN' OR `type`='MUTE' OR `type`='TEMP_MUTE' OR `type`='JAIL');".replace("{target}", t.getUniqueId().toString()));
                int puId = 0;
                FirecraftPlayer punisher;
                Punishment.Type ty = null;
                try {
                    if (set.next()) {
                        puId = set.getInt("id");
                        UUID punisherId = UUID.fromString(set.getString("punisher"));
                        punisher = plugin.getFCDatabase().getPlayer(punisherId);
                        ty = Punishment.Type.valueOf(set.getString("type"));

                        if (punisher.getMainRank().equals(Rank.FIRECRAFT_TEAM) && !player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                            player.sendMessage(Prefixes.ENFORCER + Messages.punishmentByFCT);
                            return true;
                        }
                    }
                } catch (SQLException e) {
                    player.sendMessage(Prefixes.ENFORCER + Messages.punishmentsSQLError);
                    return true;
                }

                if (ty == null) {
                    player.sendMessage(Messages.punishmentsSQLError);
                    return true;
                }
                if (cmd.getName().equalsIgnoreCase("unban")) {
                    if (!player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                        player.sendMessage(Prefixes.ENFORCER + Messages.noPermission);
                        return true;
                    }

                    if (ty.equals(Punishment.Type.BAN) || ty.equals(Punishment.Type.TEMP_BAN)) {
                        plugin.getFCDatabase().updateSQL("UPDATE `punishments` SET `active`='false', `removedby`='{remover}' WHERE `id`='{id}';".replace("{remover}", player.getUniqueId().toString()).replace("{id}", puId + ""));
                        FPacketPunishRemove punishRemove = new FPacketPunishRemove(plugin.getFCServer().getId(), puId);
                        plugin.getSocket().sendPacket(punishRemove);
                    }
                } else if (cmd.getName().equalsIgnoreCase("unmute")) {
                    if (!player.getMainRank().isEqualToOrHigher(Rank.MODERATOR)) {
                        player.sendMessage(Prefixes.ENFORCER + Messages.noPermission);
                        return true;
                    }

                    if (ty.equals(Punishment.Type.MUTE) || ty.equals(Punishment.Type.TEMP_MUTE)) {
                        plugin.getFCDatabase().updateSQL("UPDATE `punishments` SET `active`='false', `removedby`='{remover}' WHERE `id`='{id}';".replace("{remover}", player.getUniqueId().toString()).replace("{id}", puId + ""));
                        FPacketPunishRemove punishRemove = new FPacketPunishRemove(plugin.getFCServer().getId(), puId);
                        plugin.getSocket().sendPacket(punishRemove);
                    }
                } else if (cmd.getName().equalsIgnoreCase("unjail")) {
                    if (!player.getMainRank().isEqualToOrHigher(Rank.MODERATOR)) {
                        player.sendMessage(Prefixes.ENFORCER + Messages.noPermission);
                        return true;
                    }

                    if (ty.equals(Punishment.Type.JAIL)) {
                        plugin.getFCDatabase().updateSQL("UPDATE `punishments` SET `active`='false', `removedby`='{remover}' WHERE `id`='{id}';".replace("{remover}", player.getUniqueId().toString()).replace("{id}", puId + ""));
                        FPacketPunishRemove punishRemove = new FPacketPunishRemove(plugin.getFCServer().getId(), puId);
                        plugin.getSocket().sendPacket(punishRemove);
                    }
                }
            }

            //TODO The direct commands will only be available to Admin+ when the automation part is implemented.
            long date = System.currentTimeMillis();
            String punisherId = player.getUniqueId().toString();
            String targetId = t.getUniqueId().toString();
            FirecraftServer server = plugin.getFCServer();
            if (cmd.getName().equalsIgnoreCase("ban")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                    player.sendMessage(Prefixes.ENFORCER + Messages.noPermission);
                    return true;
                }

                Punishment.Type type = Punishment.Type.BAN;

                String reason = Utils.getReason(1, args);

                if (reason.isEmpty()) {
                    player.sendMessage(Prefixes.ENFORCER + Messages.punishNoReason);
                    return true;
                }

                PermanentBan permBan = new PermanentBan(type, server.getName(), punisherId, targetId, reason, date);
                permBan.setActive(true);
                Punishment permanentBan = plugin.getFCDatabase().addPunishment(permBan);
                if (permanentBan != null) {
                    if (Bukkit.getPlayer(t.getUniqueId()) != null)
                        t.kickPlayer(Utils.color(Messages.banMessage(punisherId, reason, "Permanent", permanentBan.getId())));
                    FPacketPunish punish = new FPacketPunish(server.getId(), permanentBan.getId());
                    plugin.getSocket().sendPacket(punish);
                } else {
                    player.sendMessage(Prefixes.ENFORCER + Messages.punishmentCreateIssue);
                    return true;
                }
            } else if (cmd.getName().equalsIgnoreCase("tempban")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.MODERATOR)) {
                    player.sendMessage(Prefixes.ENFORCER + Messages.noPermission);
                    return true;
                }
                String expireTime = "P";
                String time = args[1].toUpperCase();
                String[] a = time.split("d".toUpperCase());

                if (a.length == 1) {
                    expireTime += a[0].contains("H") || a[0].contains("M") || a[0].contains("S") ? "T" + a[0] : a[0] + "d";
                } else if (a.length == 2) {
                    expireTime = a[0] + "dT" + a[1];
                }

                long expire = Duration.parse(expireTime).toMillis();
                long expireDate = date + expire;
                String reason = Utils.getReason(2, args);
                if (reason.isEmpty()) {
                    player.sendMessage(Prefixes.ENFORCER + Messages.punishNoReason);
                    return true;
                }
                Punishment.Type type = Punishment.Type.TEMP_BAN;
                TemporaryBan tempBan = new TemporaryBan(type, server.getName(), punisherId, targetId, reason, date, expireDate);
                tempBan.setActive(true);
                TemporaryPunishment temporaryBan = (TemporaryPunishment) plugin.getFCDatabase().addPunishment(tempBan);
                if (temporaryBan == null) {
                    player.sendMessage(Prefixes.ENFORCER + Messages.punishmentCreateIssue);
                    return true;
                }
                if (Bukkit.getPlayer(t.getUniqueId()) != null) {
                    t.kickPlayer(Utils.color(Messages.banMessage(punisherId, reason, temporaryBan.formatExpireTime(), temporaryBan.getId())));
                }
                FPacketPunish punish = new FPacketPunish(server.getId(), temporaryBan.getId());
                plugin.getSocket().sendPacket(punish);
            } else if (cmd.getName().equalsIgnoreCase("ipban")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.HEAD_ADMIN)) {
                    player.sendMessage(Prefixes.ENFORCER + Messages.noPermission);
                    return true;
                }
            } else if (cmd.getName().equalsIgnoreCase("mute")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.MODERATOR)) {
                    player.sendMessage(Prefixes.ENFORCER + Messages.noPermission);
                    return true;
                }

                Punishment.Type type = Punishment.Type.MUTE;

                String reason = Utils.getReason(1, args);
                if (reason.isEmpty()) {
                    player.sendMessage(Prefixes.ENFORCER + Messages.punishNoReason);
                    return true;
                }

                PermanentMute permMute = new PermanentMute(type, server.getName(), punisherId, targetId, reason, date);
                permMute.setActive(true);
                Punishment permanentMute = plugin.getFCDatabase().addPunishment(permMute);
                if (permanentMute != null) {
                    FPacketPunish punish = new FPacketPunish(server.getId(), permanentMute.getId());
                    plugin.getSocket().sendPacket(punish);
                } else {
                    player.sendMessage(Prefixes.ENFORCER + Messages.punishmentCreateIssue);
                    return true;
                }
                if (Bukkit.getPlayer(t.getUniqueId()) != null)
                    t.sendMessage(Messages.permMuteTarget(player.getName()));
            } else if (cmd.getName().equalsIgnoreCase("tempmute")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.HELPER)) {
                    player.sendMessage(Prefixes.ENFORCER + Messages.noPermission);
                    return true;
                }
                String expireTime = "P";
                String time = args[1].toUpperCase();
                String[] a = time.split("d".toUpperCase());

                if (a.length == 1) {
                    expireTime += a[0].contains("H") || a[0].contains("M") || a[0].contains("S") ? "T" + a[0] : a[0] + "d";
                } else if (a.length == 2) {
                    expireTime = a[0] + "dT" + a[1];
                }

                long expire = Duration.parse(expireTime).toMillis();
                long expireDate = date + expire;
                String reason = Utils.getReason(2, args);
                if (reason.isEmpty()) {
                    player.sendMessage(Prefixes.ENFORCER + Messages.punishNoReason);
                    return true;
                }
                Punishment.Type type = Punishment.Type.TEMP_MUTE;
                TemporaryBan tempBan = new TemporaryBan(type, server.getName(), punisherId, targetId, reason, date, expireDate);
                tempBan.setActive(true);
                TemporaryPunishment temporaryMute = (TemporaryPunishment) plugin.getFCDatabase().addPunishment(tempBan);
                if (temporaryMute == null) {
                    player.sendMessage(Prefixes.ENFORCER + Messages.punishmentCreateIssue);
                    return true;
                }
                if (Bukkit.getPlayer(t.getUniqueId()) != null) {
                    t.sendMessage(Messages.tempMuteTarget(player.getName()));
                }
                FPacketPunish punish = new FPacketPunish(server.getId(), temporaryMute.getId());
                plugin.getSocket().sendPacket(punish);
            } else if (cmd.getName().equalsIgnoreCase("jail")) {
                if (!player.getMainRank().equals(Rank.HELPER)) {
                    player.sendMessage(Prefixes.ENFORCER + Messages.onlyHelpersJail);
                    return true;
                }

                Location jailLocation = plugin.getJailLocation();
                if (jailLocation == null) {
                    player.sendMessage(Prefixes.ENFORCER + Messages.jailNotSet);
                    return true;
                }

                String reason = Utils.getReason(1, args);
                if (reason.isEmpty()) {
                    player.sendMessage(Prefixes.ENFORCER + Messages.punishNoReason);
                    return true;
                }
                Jail j = new Jail(server.getName(), punisherId, targetId, reason, date);
                j.setActive(true);
                Punishment jail = plugin.getFCDatabase().addPunishment(j);
                if (jail != null) {
                    FPacketPunish punish = new FPacketPunish(server.getId(), jail.getId());
                    plugin.getSocket().sendPacket(punish);
                    if (Bukkit.getPlayer(t.getUniqueId()) != null) {
                        t.sendMessage(Messages.jailed(player.getName(), reason));
                        t.teleport(jailLocation);
                    }
                } else {
                    player.sendMessage(Prefixes.ENFORCER + Messages.punishmentCreateIssue);
                    return true;
                }
            } else if (cmd.getName().equalsIgnoreCase("kick")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.HELPER)) {
                    player.sendMessage(Prefixes.ENFORCER + Messages.noPermission);
                    return true;
                }

                Punishment.Type type = Punishment.Type.KICK;
                String reason = Utils.getReason(1, args);
                if (reason.isEmpty()) {
                    player.sendMessage(Prefixes.ENFORCER + Messages.punishNoReason);
                    return true;
                }
                Kick k = new Kick(type, server.getName(), punisherId, targetId, reason, date);

                Kick kick = (Kick) plugin.getFCDatabase().addPunishment(k);
                if (Bukkit.getPlayer(t.getUniqueId()) != null)
                    t.kickPlayer(Messages.kickMessage(player.getName(), reason));
                if (kick != null) {
                    FPacketPunish punish = new FPacketPunish(server.getId(), kick.getId());
                    plugin.getSocket().sendPacket(punish);
                } else {
                    player.sendMessage(Prefixes.ENFORCER + Messages.punishmentCreateIssue);
                    return true;
                }
            } else if (cmd.getName().equalsIgnoreCase("warn")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.HELPER)) {
                    player.sendMessage(Prefixes.ENFORCER + Messages.noPermission);
                    return true;
                }

                String reason = Utils.getReason(1, args);
                if (reason.isEmpty()) {
                    player.sendMessage(Prefixes.ENFORCER + Messages.punishNoReason);
                    return true;
                }
                Warn w = new Warn(server.getName(), punisherId, targetId, reason, date);
                w.setActive(true);
                Warn warn = (Warn) plugin.getFCDatabase().addPunishment(w);
                if (warn != null) {
                    FPacketPunish punish = new FPacketPunish(server.getId(), warn.getId());
                    plugin.getSocket().sendPacket(punish);
                    if (Bukkit.getPlayer(t.getUniqueId()) != null) {
                        String code = Utils.generateAckCode(Utils.codeCharacters);
                        plugin.addAckCode(t.getUniqueId(), code);
                        t.sendMessage(Messages.warnMessage(player.getName(), reason, code));
                    }
                } else {
                    player.sendMessage(Prefixes.ENFORCER + Messages.punishmentCreateIssue);
                    return true;
                }
            } else {
                if (!(args.length == 0)) {
                    player.sendMessage(Prefixes.ENFORCER + Messages.notEnoughArgs);
                    return true;
                }

                FirecraftPlayer target = plugin.getPlayerManager().getPlayer(args[0]);

                if (cmd.getName().equalsIgnoreCase("history")) {
                    List<Punishment> punishments = plugin.getFCDatabase().getPunishments(target.getUniqueId());

                } else if (cmd.getName().equalsIgnoreCase("bans")) {

                } else if (cmd.getName().equalsIgnoreCase("mutes")) {

                } else if (cmd.getName().equalsIgnoreCase("kicks")) {

                } else if (cmd.getName().equalsIgnoreCase("warns")) {

                } else if (cmd.getName().equalsIgnoreCase("jails")) {

                }
            }
        } else {
            sender.sendMessage(Prefixes.ENFORCER + "§cNot implemented yet.");
        }

        return true;
    }
}