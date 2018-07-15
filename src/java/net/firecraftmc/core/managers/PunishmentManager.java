package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.*;
import net.firecraftmc.shared.classes.enums.Rank;
import net.firecraftmc.shared.classes.model.player.FirecraftPlayer;
import net.firecraftmc.shared.classes.model.server.FirecraftServer;
import net.firecraftmc.shared.command.FirecraftCommand;
import net.firecraftmc.shared.packets.*;
import net.firecraftmc.shared.punishments.Enforcer;
import net.firecraftmc.shared.punishments.Punishment;
import net.firecraftmc.shared.punishments.Punishment.Type;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class PunishmentManager implements Listener {
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
        
        
        FirecraftCommand setJail = new FirecraftCommand("setjail", "Sets the jail location for the server.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                plugin.setJailLocation(player.getLocation());
                player.sendMessage(Prefixes.ENFORCER + Messages.setJail);
            }
        }.setBaseRank(Rank.ADMIN).addAlias("sj");
        
        FirecraftCommand ban = new FirecraftCommand("ban", "Permanently bans a player from the server") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                handlePermPunishmentCommand(player, args, Type.BAN);
            }
        }.setBaseRank(Rank.ADMIN);
        
        FirecraftCommand tempban = new FirecraftCommand("tempban", "Temporarily bans a player from the server") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                handleTempPunishCommand(player, args, Type.TEMP_BAN);
            }
        }.setBaseRank(Rank.MODERATOR);
        
        FirecraftCommand mute = new FirecraftCommand("mute", "Permanently mutes a player") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                handlePermPunishmentCommand(player, args, Type.MUTE);
            }
        }.setBaseRank(Rank.MODERATOR);
        
        FirecraftCommand tempmute = new FirecraftCommand("tempmute", "Temporarily mutes a player") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                handleTempPunishCommand(player, args, Type.TEMP_MUTE);
            }
        }.setBaseRank(Rank.MODERATOR);
        
        FirecraftCommand jail = new FirecraftCommand("jail", "Jails a player on the server") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                FirecraftPlayer t = getTarget(player, args[0]);
                
                Location jailLocation = plugin.getJailLocation();
                if (jailLocation == null) {
                    player.sendMessage(Prefixes.ENFORCER + Messages.jailNotSet);
                    return;
                }
                
                String reason = Utils.getReason(1, args);
                if (reason.isEmpty()) {
                    player.sendMessage(Prefixes.ENFORCER + Messages.punishNoReason);
                    return;
                }
                
                Punishment punishment = Enforcer.createPunishment(t, player, Type.JAIL, reason, System.currentTimeMillis(), -1);
                FPacketPunish punish = new FPacketPunish(plugin.getFCServer().getName(), punishment.getId());
                plugin.getSocket().sendPacket(punish);
                
                if (Bukkit.getPlayer(t.getUniqueId()) != null) {
                    t.sendMessage(Messages.jailed(player.getName(), reason));
                    t.teleport(jailLocation);
                }
            }
        }.setBaseRank(Rank.MODERATOR);
        
        FirecraftCommand kick = new FirecraftCommand("kick", "Kicks a player from the server") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                FirecraftPlayer t = getTarget(player, args[0]);
                String reason = Utils.getReason(1, args);
                if (reason.isEmpty()) {
                    player.sendMessage(Prefixes.ENFORCER + Messages.punishNoReason);
                    return;
                }
                Punishment punishment = Enforcer.createPunishment(t, player, Type.JAIL, reason, System.currentTimeMillis(), -1);
                FPacketPunish punish = new FPacketPunish(plugin.getFCServer().getName(), punishment.getId());
                plugin.getSocket().sendPacket(punish);
    
                if (Bukkit.getPlayer(t.getUniqueId()) != null)
                    t.kickPlayer(Messages.kickMessage(player.getName(), reason));
            }
        }.setBaseRank(Rank.MODERATOR);
        
        FirecraftCommand warn = new FirecraftCommand("warn", "Warns a player on the server") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                FirecraftPlayer t = getTarget(player, args[0]);
                String reason = Utils.getReason(1, args);
                if (reason.isEmpty()) {
                    player.sendMessage(Prefixes.ENFORCER + Messages.punishNoReason);
                    return;
                }
                Punishment punishment = Enforcer.createPunishment(t, player, Type.JAIL, reason, System.currentTimeMillis(), -1);
                FPacketPunish punish = new FPacketPunish(plugin.getFCServer().getName(), punishment.getId());
                plugin.getSocket().sendPacket(punish);
                if (punishment != null) {
                    if (Bukkit.getPlayer(t.getUniqueId()) != null) {
                        String code = Utils.generateAckCode(Utils.codeCharacters);
                        plugin.addAckCode(t.getUniqueId(), code);
                        t.sendMessage(Messages.warnMessage(player.getName(), reason, code));
                    }
                }
            }
        }.setBaseRank(Rank.MODERATOR);
        
        FirecraftCommand unban = new FirecraftCommand("unban", "Removes all active bans from a player") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                handleUnpunishCommand(player, args);
            }
        }.setBaseRank(Rank.MODERATOR);
        
        FirecraftCommand unmute = new FirecraftCommand("unmute", "Removes all active mutes from a player") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                handleUnpunishCommand(player, args);
            }
        }.setBaseRank(Rank.MODERATOR);
        
        FirecraftCommand unjail = new FirecraftCommand("unjail", "Removes active jails from a player") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                handleUnpunishCommand(player, args);
            }
        }.setBaseRank(Rank.MODERATOR);
        
        
        plugin.getCommandManager().addCommands(setJail, ban, tempban, mute, tempmute, jail, kick, warn, unban, unmute, unjail);
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
                    e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Utils.color(Messages.banMessage(punishment, expireDiff)));
                } else if (punishment.getType().equals(Punishment.Type.BAN)) {
                    e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Utils.color(Messages.banMessage(punishment, "Permanent")));
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
    
    private boolean sendPunishment(FirecraftPlayer player, FirecraftServer server, Punishment punishment) {
        if (punishment != null) {
            FPacketPunish punish = new FPacketPunish(server.getId(), punishment.getId());
            plugin.getSocket().sendPacket(punish);
        } else {
            player.sendMessage(Prefixes.ENFORCER + Messages.punishmentCreateIssue);
            return true;
        }
        return false;
    }
    
    private FirecraftPlayer getTarget(FirecraftPlayer player, String a) {
        UUID uuid;
        try {
            uuid = UUID.fromString(a);
        } catch (Exception e) {
            uuid = Utils.Mojang.getUUIDFromName(a);
        }
        
        if (uuid == null) {
            player.sendMessage(Prefixes.ENFORCER + Messages.punishInvalidTarget);
            return null;
        }
        
        return plugin.getPlayerManager().getPlayer(uuid);
    }
    
    private boolean checkAllowedToPunish(FirecraftPlayer player, FirecraftPlayer t) {
        if (t.getMainRank().isEqualToOrHigher(player.getMainRank())) {
            if (!player.getUniqueId().equals(FirecraftMC.firestar311)) {
                player.sendMessage(Prefixes.ENFORCER + Messages.noPunishRank);
                return false;
            }
        }
        return true;
    }
    
    private void handlePermPunishmentCommand(FirecraftPlayer player, String[] args, Type type) {
        if (!(args.length > 1)) {
            player.sendMessage(Prefixes.ENFORCER + "<ec>You do not have enough arguments.");
            return;
        }
        
        FirecraftPlayer target = getTarget(player, args[0]);
        
        Punishment punishment = Enforcer.createPunishment(target, player, type, Utils.getReason(1, args), System.currentTimeMillis(), 0);
        if (punishment == null) {
            player.sendMessage(Prefixes.ENFORCER + "<ec>There was an error creating the punishment.");
            return;
        }
        
        punishment.setPunisherName(player.getName());
        punishment.setTargetName(player.getName());
        
        if (Bukkit.getPlayer(target.getUniqueId()) != null)
            target.kickPlayer(Utils.color(Messages.banMessage(punishment, "Permanent")));
        
        FPacketPunish punish = new FPacketPunish(FirecraftMC.getServer().getId(), punishment.getId());
        plugin.getSocket().sendPacket(punish);
    }
    
    private void handleTempPunishCommand(FirecraftPlayer player, String[] args, Type type) {
        if (!(args.length > 2)) {
            player.sendMessage(Prefixes.ENFORCER + "<ec>You do not have enough arguments.");
            return;
        }
        
        FirecraftPlayer target = getTarget(player, args[0]);
        long date = System.currentTimeMillis();
        long expireDate = Enforcer.calculateExpireDate(date, args[1]);
        
        Punishment punishment = Enforcer.createPunishment(target, player, type, Utils.getReason(2, args), System.currentTimeMillis(), expireDate);
        if (punishment == null) {
            player.sendMessage(Prefixes.ENFORCER + "<ec>There was an error creating the punishment.");
            return;
        }
    
        punishment.setPunisherName(player.getName());
        punishment.setTargetName(player.getName());
        
        if (type.equals(Type.TEMP_BAN)) {
            if (Bukkit.getPlayer(target.getUniqueId()) != null) {
                target.kickPlayer(Utils.color(Messages.banMessage(punishment, punishment.formatExpireTime())));
            }
        }
        
        FPacketPunish punish = new FPacketPunish(FirecraftMC.getServer().getId(), punishment.getId());
        plugin.getSocket().sendPacket(punish);
    }
    
    private void handleUnpunishCommand(FirecraftPlayer player, String[] args) {
        if (!(args.length == 1)) {
            player.sendMessage(Prefixes.ENFORCER + "<ec>You have an invalid amount of arguments.");
            return;
        }
        
        FirecraftPlayer target = getTarget(player, args[0]);
        ResultSet set = plugin.getFCDatabase().querySQL("SELECT * FROM `punishments` WHERE `target`='{target}' AND `active`='true' AND (`type`='BAN' OR `type`='TEMP_BAN' OR `type`='MUTE' OR `type`='TEMP_MUTE' OR `type`='JAIL');".replace("{target}", target.getUniqueId().toString()));
        Punishment punishment = null;
        try {
            if (set.next()) {
                punishment = plugin.getFCDatabase().getPunishment(set.getInt("id"));
            }
        } catch (SQLException e) {
            player.sendMessage(Prefixes.ENFORCER + Messages.punishmentsSQLError);
            return;
        }
        
        FirecraftPlayer punisher = plugin.getPlayerManager().getPlayer(punishment.getPunisher());
        if (punisher.getMainRank().equals(Rank.FIRECRAFT_TEAM) && !player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
            player.sendMessage(Prefixes.ENFORCER + Messages.punishmentByFCT);
            return;
        }
        
        plugin.getFCDatabase().updateSQL("UPDATE `punishments` SET `active`='false', `removedby`='{remover}' WHERE `id`='{id}';".replace("{remover}", player.getUniqueId().toString()).replace("{id}", punishment.getId() + ""));
        FPacketPunishRemove punishRemove = new FPacketPunishRemove(plugin.getFCServer().getId(), punishment.getId());
        plugin.getSocket().sendPacket(punishRemove);
    }
}