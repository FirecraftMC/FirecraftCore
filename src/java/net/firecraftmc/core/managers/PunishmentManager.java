package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.FirecraftPlayer;
import net.firecraftmc.shared.classes.FirecraftServer;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.enforcer.Enforcer;
import net.firecraftmc.shared.enforcer.Type;
import net.firecraftmc.shared.enforcer.punishments.*;
import net.firecraftmc.shared.enums.Rank;
import net.firecraftmc.shared.packets.FPacketPunish;
import net.firecraftmc.shared.packets.FPacketPunishRemove;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
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

public class PunishmentManager implements TabExecutor, Listener {
    private FirecraftCore plugin;
    private static final String prefix = "&d&l[ENFORCER] ";
    private final UUID firestar311 = UUID.fromString("3f7891ce-5a73-4d52-a2ba-299839053fdc");
    
    
    public PunishmentManager(FirecraftCore plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        UUID uuid = e.getUniqueId();
        ResultSet set = plugin.getDatabase().querySQL("SELECT * from `punishments` WHERE `target`='" + uuid.toString().replace("-", "") + "';");
        if (set != null) {
            try {
                while (set.next()) {
                    if (set.getBoolean("active")) {
                        int id = set.getInt("id");
                        Type type = Type.valueOf(set.getString("type"));
                        FirecraftPlayer punisher = Utils.Database.getPlayerFromDatabase(plugin.getFirecraftServer(), plugin.getDatabase(), Utils.convertToUUID(set.getString("punisher")));
                        String reason = set.getString("reason");
                        if (type.equals(Type.TEMP_BAN)) {
                            long expire = set.getLong("expire");
                            String expireDiff = Utils.Time.formatTime(expire - System.currentTimeMillis());
                            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Utils.color(Messages.banMessage(punisher.getName(), reason, expireDiff, id)));
                        } else if (type.equals(Type.BAN)) {
                            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Utils.color(Messages.banMessage(punisher.getName(), reason, "Permanent", id)));
                        }
                    }
                }
            } catch (SQLException e1) {
                e1.printStackTrace();
            }
        }
    }

    @EventHandler
    public void onCommandPreProcess(PlayerCommandPreprocessEvent e) {
        FirecraftPlayer player = Utils.Database.getPlayerFromDatabase(plugin.getFirecraftServer(), plugin.getDatabase(), e.getPlayer().getUniqueId());
        ResultSet jailSet = plugin.getDatabase().querySQL("SELECT * FROM `punishments` WHERE `target`='{uuid}' AND `active`='true' AND `type`='JAIL';".replace("{uuid}", player.getUniqueId().toString().replace("-", "")));
        try {
            if (jailSet.next()) {
                player.sendMessage(Messages.jailedNoCmds);
                e.setCancelled(true);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        ResultSet warnSet = plugin.getDatabase().querySQL("SELECT * FROM `punishments` WHERE `target`='{uuid}' AND `acknowledged`='false' AND `type`='WARN';".replace("{uuid}", player.getUniqueId().toString().replace("-", "")));
        try {
            if (warnSet.next()) {
                player.sendMessage(Messages.unAckWarnNoCmds);
                e.setCancelled(true);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (sender instanceof Player) {
            FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
            if (cmd.getName().equalsIgnoreCase("setjail")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                    player.sendMessage(prefix + Messages.noPermission);
                    return true;
                }
    
                plugin.setJailLocation(player.getLocation());
                player.sendMessage(prefix + Messages.setJail);
                return true;
            }
    
            if (!(args.length > 0)) {
                player.sendMessage(prefix + "&cYou must provide a name or uuid to punish.");
                return true;
            }
    
            UUID uuid;
            try {
                uuid = UUID.fromString(args[0]);
            } catch (Exception e) {
                uuid = Utils.Mojang.getUUIDFromName(args[0]);
            }
    
            if (uuid == null) {
                player.sendMessage(prefix + Messages.punishInvalidTarget);
                return true;
            }
    
            FirecraftPlayer t = plugin.getPlayerManager().getPlayer(uuid);
            if (t == null) {
                t = Utils.Database.getPlayerFromDatabase(plugin.getFirecraftServer(), plugin.getDatabase(), uuid);
                if (t == null) {
                    player.sendMessage(prefix + Messages.profileNotFound);
                    return true;
                }
            }
    
            if (t.getMainRank().isEqualToOrHigher(player.getMainRank())) {
                if (!player.getUniqueId().equals(firestar311)) {
                    player.sendMessage(prefix + Messages.noPunishRank);
                    return true;
                }
            }
    
            if (cmd.getName().equalsIgnoreCase("unban") || cmd.getName().equalsIgnoreCase("unmute") || cmd.getName().equalsIgnoreCase("unjail")) {
                ResultSet set = plugin.getDatabase().querySQL("SELECT * FROM `punishments` WHERE `target`='{target}' AND `active`='true' AND (`type`='BAN' OR `type`='TEMP_BAN' OR `type`='MUTE' OR `type`='TEMP_MUTE' OR `type`='JAIL');".replace("{target}", t.getUniqueId().toString().replace("-", "")));
                int puId = 0;
                FirecraftPlayer punisher;
                Type ty = null;
                try {
                    if (set.next()) {
                        puId = set.getInt("id");
                        UUID punisherId = Utils.convertToUUID(set.getString("punisher"));
                        punisher = Utils.Database.getPlayerFromDatabase(plugin.getFirecraftServer(), plugin.getDatabase(), punisherId);
                        ty = Type.valueOf(set.getString("type"));
            
                        if (punisher.getMainRank().equals(Rank.FIRECRAFT_TEAM) && !player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                            player.sendMessage(prefix + Messages.punishmentByFCT);
                            return true;
                        }
                    }
                } catch (SQLException e) {
                    player.sendMessage(prefix + Messages.punishmentsSQLError);
                    return true;
                }
    
                if (ty == null) {
                    player.sendMessage(Messages.punishmentsSQLError);
                    return true;
                }
                if (cmd.getName().equalsIgnoreCase("unban")) {
                    if (!player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                        player.sendMessage(prefix + Messages.noPermission);
                        return true;
                    }
        
                    if (ty.equals(Type.BAN) || ty.equals(Type.TEMP_BAN)) {
                        plugin.getDatabase().updateSQL("UPDATE `punishments` SET `active`='false', `removedby`='{remover}' WHERE `id`='{id}';".replace("{remover}", player.getUniqueId().toString().replace("-", "")).replace("{id}", puId + ""));
                        FPacketPunishRemove punishRemove = new FPacketPunishRemove(plugin.getFirecraftServer(), puId);
                        plugin.getSocket().sendPacket(punishRemove);
                    }
                } else if (cmd.getName().equalsIgnoreCase("unmute")) {
                    if (!player.getMainRank().isEqualToOrHigher(Rank.MOD)) {
                        player.sendMessage(prefix + Messages.noPermission);
                        return true;
                    }
        
                    if (ty.equals(Type.MUTE) || ty.equals(Type.TEMP_MUTE)) {
                        plugin.getDatabase().updateSQL("UPDATE `punishments` SET `active`='false', `removedby`='{remover}' WHERE `id`='{id}';".replace("{remover}", player.getUniqueId().toString().replace("-", "")).replace("{id}", puId + ""));
                        FPacketPunishRemove punishRemove = new FPacketPunishRemove(plugin.getFirecraftServer(), puId);
                        plugin.getSocket().sendPacket(punishRemove);
                    }
                } else if (cmd.getName().equalsIgnoreCase("unjail")) {
                    if (!player.getMainRank().isEqualToOrHigher(Rank.MOD)) {
                        player.sendMessage(prefix + Messages.noPermission);
                        return true;
                    }
        
                    if (ty.equals(Type.JAIL)) {
                        plugin.getDatabase().updateSQL("UPDATE `punishments` SET `active`='false', `removedby`='{remover}' WHERE `id`='{id}';".replace("{remover}", player.getUniqueId().toString().replace("-", "")).replace("{id}", puId + ""));
                        FPacketPunishRemove punishRemove = new FPacketPunishRemove(plugin.getFirecraftServer(), puId);
                        plugin.getSocket().sendPacket(punishRemove);
                    }
                }
            }
            
            //TODO The direct commands will only be available to Admin+ when the automation part is implemented.
            long date = System.currentTimeMillis();
            String punisherId = player.getUniqueId().toString().replace("-", "");
            String targetId = t.getUniqueId().toString().replace("-", "");
            FirecraftServer server = plugin.getFirecraftServer();
            if (cmd.getName().equalsIgnoreCase("ban")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                    player.sendMessage(prefix + Messages.noPermission);
                    return true;
                }
                
                Type type = Type.BAN;
                
                String reason = getReason(1, args);
                
                if (reason.length() == 0) {
                    player.sendMessage(prefix + Messages.punishNoReason);
                    return true;
                }
                
                PermanentBan permBan = new PermanentBan(type, server.getName(), punisherId, targetId, reason, date);
                permBan.setActive(true);
                Punishment permanentBan = Enforcer.addToDatabase(plugin.getDatabase(), permBan);
                if (permanentBan != null) {
                    if (Bukkit.getPlayer(t.getUniqueId()) != null)
                        t.kickPlayer(Utils.color(Messages.banMessage(punisherId, reason, "Permanent", permanentBan.getId())));
                    FPacketPunish punish = new FPacketPunish(server, permanentBan.getId());
                    plugin.getSocket().sendPacket(punish);
                } else {
                    player.sendMessage(prefix + Messages.punishmentCreateIssue);
                    return true;
                }
            } else if (cmd.getName().equalsIgnoreCase("tempban")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.MOD)) {
                    player.sendMessage(prefix + Messages.noPermission);
                    return true;
                }
                String expireTime = "P";
                String time = args[1].toUpperCase();
                String[] a = time.split("D");

                if (a.length == 1) {
                    if (a[0].contains("h") || a[0].contains("m") || a[0].contains("s")) {
                        expireTime += "T" + a[0];
                    } else {
                        expireTime += a[0] + "d";
                    }
                } else if (a.length == 2) {
                    expireTime = a[0] + "dT" + a[1];
                }

                long expire = Duration.parse(expireTime).toMillis();
                long expireDate = date + expire;
                String reason = getReason(2, args);
                if (reason.length() == 0) {
                    player.sendMessage(prefix + Messages.punishNoReason);
                    return true;
                }
                Type type = Type.TEMP_BAN;
                TemporaryBan tempBan = new TemporaryBan(type, server.getName(), punisherId, targetId, reason, date, expireDate);
                tempBan.setActive(true);
                TemporaryPunishment temporaryBan = (TemporaryPunishment) Enforcer.addToDatabase(plugin.getDatabase(), tempBan);
                if (temporaryBan == null) {
                    player.sendMessage(prefix + Messages.punishmentCreateIssue);
                    return true;
                }
                if (Bukkit.getPlayer(t.getUniqueId()) != null) {
                    t.kickPlayer(Utils.color(Messages.banMessage(punisherId, reason, temporaryBan.formatExpireTime(), temporaryBan.getId())));
                }
                FPacketPunish punish = new FPacketPunish(server, temporaryBan.getId());
                plugin.getSocket().sendPacket(punish);
            } else if (cmd.getName().equalsIgnoreCase("ipban")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.HEAD_ADMIN)) {
                    player.sendMessage(prefix + Messages.noPermission);
                    return true;
                }
            } else if (cmd.getName().equalsIgnoreCase("mute")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.MOD)) {
                    player.sendMessage(prefix + Messages.noPermission);
                    return true;
                }
    
                Type type = Type.MUTE;
    
                String reason = getReason(1, args);
                if (reason.length() == 0) {
                    player.sendMessage(prefix + Messages.punishNoReason);
                    return true;
                }
    
                PermanentMute permMute = new PermanentMute(type, server.getName(), punisherId, targetId, reason, date);
                permMute.setActive(true);
                Punishment permanentMute = Enforcer.addToDatabase(plugin.getDatabase(), permMute);
                if (permanentMute != null) {
                    FPacketPunish punish = new FPacketPunish(server, permanentMute.getId());
                    plugin.getSocket().sendPacket(punish);
                } else {
                    player.sendMessage(prefix + Messages.punishmentCreateIssue);
                    return true;
                }
                if (Bukkit.getPlayer(t.getUniqueId()) != null)
                    t.sendMessage(Messages.permMuteTarget(player.getName()));
            } else if (cmd.getName().equalsIgnoreCase("tempmute")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.HELPER)) {
                    player.sendMessage(prefix + Messages.noPermission);
                    return true;
                }
                String expireTime = "P";
                String time = args[1].toUpperCase();
                String[] a = time.split("d".toUpperCase());

                if (a.length == 1) {
                    if (a[0].contains("h") || a[0].contains("m") || a[0].contains("s")) {
                        expireTime += "T" + a[0];
                    } else {
                        expireTime += a[0] + "d";
                    }
                } else if (a.length == 2) {
                    expireTime = a[0] + "dT" + a[1];
                }

                long expire = Duration.parse(expireTime).toMillis();
                long expireDate = date + expire;
                String reason = getReason(2, args);
                if (reason.length() == 0) {
                    player.sendMessage(prefix + Messages.punishNoReason);
                    return true;
                }
                Type type = Type.TEMP_MUTE;
                TemporaryBan tempBan = new TemporaryBan(type, server.getName(), punisherId, targetId, reason, date, expireDate);
                tempBan.setActive(true);
                TemporaryPunishment temporaryMute = (TemporaryPunishment) Enforcer.addToDatabase(plugin.getDatabase(), tempBan);
                if (temporaryMute == null) {
                    player.sendMessage(prefix + Messages.punishmentCreateIssue);
                    return true;
                }
                if (Bukkit.getPlayer(t.getUniqueId()) != null) {
                    t.sendMessage(Messages.tempMuteTarget(player.getName()));
                }
                FPacketPunish punish = new FPacketPunish(server, temporaryMute.getId());
                plugin.getSocket().sendPacket(punish);
            } else if (cmd.getName().equalsIgnoreCase("jail")) {
                if (!player.getMainRank().equals(Rank.HELPER)) {
                    player.sendMessage(prefix + Messages.onlyHelpersJail);
                    return true;
                }
    
                Location jailLocation = plugin.getJailLocation();
                if (jailLocation == null) {
                    player.sendMessage(prefix + Messages.jailNotSet);
                    return true;
                }
    
                String reason = getReason(1, args);
                if (reason.length() == 0) {
                    player.sendMessage(prefix + Messages.punishNoReason);
                    return true;
                }
                Jail j = new Jail(server.getName(), punisherId, targetId, reason, date);
                j.setActive(true);
                Punishment jail = Enforcer.addToDatabase(plugin.getDatabase(), j);
                if (jail != null) {
                    FPacketPunish punish = new FPacketPunish(server, jail.getId());
                    plugin.getSocket().sendPacket(punish);
                    if (Bukkit.getPlayer(t.getUniqueId()) != null) {
                        t.sendMessage(Messages.jailed(player.getName(), reason));
                        t.teleport(jailLocation);
                    }
                } else {
                    player.sendMessage(prefix + Messages.punishmentCreateIssue);
                    return true;
                }
            } else if (cmd.getName().equalsIgnoreCase("kick")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.HELPER)) {
                    player.sendMessage(prefix + Messages.noPermission);
                    return true;
                }
                
                Type type = Type.KICK;
                String reason = getReason(1, args);
                if (reason.length() == 0) {
                    player.sendMessage(prefix + Messages.punishNoReason);
                    return true;
                }
                Kick k = new Kick(type, server.getName(), punisherId, targetId, reason, date);
                
                Kick kick = (Kick) Enforcer.addToDatabase(plugin.getDatabase(), k);
                if (Bukkit.getPlayer(t.getUniqueId()) != null)
                    t.kickPlayer(Messages.kickMessage(player.getName(), reason));
                if (kick != null) {
                    FPacketPunish punish = new FPacketPunish(server, kick.getId());
                    plugin.getSocket().sendPacket(punish);
                } else {
                    player.sendMessage(prefix + Messages.punishmentCreateIssue);
                    return true;
                }
            } else if (cmd.getName().equalsIgnoreCase("warn")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.HELPER)) {
                    player.sendMessage(prefix + Messages.noPermission);
                    return true;
                }
    
                String reason = getReason(1, args);
                if (reason.length() == 0) {
                    player.sendMessage(prefix + Messages.punishNoReason);
                    return true;
                }
                Warn w = new Warn(server.getName(), punisherId, targetId, reason, date);
                w.setActive(true);
                Warn warn = (Warn) Enforcer.addToDatabase(plugin.getDatabase(), w);
                if (warn != null) {
                    FPacketPunish punish = new FPacketPunish(server, warn.getId());
                    plugin.getSocket().sendPacket(punish);
                    if (Bukkit.getPlayer(t.getUniqueId()) != null) {
                        String code = Utils.generateAckCode(Utils.codeCharacters);
                        plugin.addAckCode(t.getUniqueId(), code);
                        t.sendMessage(Messages.warnMessage(player.getName(), reason, code));
                    }
                } else {
                    player.sendMessage(prefix + Messages.punishmentCreateIssue);
                    return true;
                }
            }
        } else {
            sender.sendMessage(prefix + "§cNot implemented yet.");
        }
        
        return true;
    }
    
    public List<String> onTabComplete(CommandSender sender, Command cmd, String s, String[] args) {
        return null;
    }
    
    private String getReason(int start, String[] args) {
        StringBuilder reasonBuilder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            reasonBuilder.append(args[i]);
            if (!(i == args.length - 1)) {
                reasonBuilder.append(" ");
            }
        }
        
        return reasonBuilder.toString();
    }
}