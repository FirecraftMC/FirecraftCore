package net.firecraftmc.core.managers;

import net.firecraftmc.api.FirecraftAPI;
import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.model.server.FirecraftServer;
import net.firecraftmc.api.packets.*;
import net.firecraftmc.api.paginator.Paginator;
import net.firecraftmc.api.paginator.PaginatorFactory;
import net.firecraftmc.api.punishments.*;
import net.firecraftmc.api.punishments.Punishment.Type;
import net.firecraftmc.api.util.*;
import net.firecraftmc.core.FirecraftCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.sql.ResultSet;
import java.util.*;

public class PunishmentManager implements Listener {
    private final FirecraftCore plugin;
    
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
            } else if (packet instanceof FPacketMuteExpire) {
                Punishment punishment = plugin.getFCDatabase().getPunishment(((FPacketMuteExpire) packet).getMuteId());
                FirecraftPlayer target = plugin.getPlayer(punishment.getTarget());
                target.sendMessage("<nc>Your mute has expired. Please allow up to a minute to be able to talk again.");
                if (target.getScoreboard() != null) {
                    target.getScoreboard().updateScoreboard(target);
                }
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
        }.setBaseRank(Rank.MOD);
        
        FirecraftCommand mute = new FirecraftCommand("mute", "Permanently mutes a player") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                handlePermPunishmentCommand(player, args, Type.MUTE);
            }
        }.setBaseRank(Rank.MOD);
        
        FirecraftCommand tempmute = new FirecraftCommand("tempmute", "Temporarily mutes a player") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                handleTempPunishCommand(player, args, Type.TEMP_MUTE);
            }
        }.setBaseRank(Rank.MOD);
        
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
                    t.sendMessage("");
                    t.sendMessage("&4&l╔══════════════════════════");
                    t.sendMessage("&4&l║ &c&lYou have been jailed!");
                    t.sendMessage("&4&l║");
                    t.sendMessage("&4&l║ <nc>The reason is <vc>" + punishment.getReason());
                    t.sendMessage("&4&l║ <nc>The staff member that jailed you is <vc>" + punishment.getPunisherName());
                    t.sendMessage("&4&l║");
                    t.sendMessage("&4&l╚══════════════════════════");
                    t.sendMessage("");
                    t.teleport(jailLocation);
                }
            }
        }.setBaseRank(Rank.MOD);
        
        FirecraftCommand kick = new FirecraftCommand("kick", "Kicks a player from the server") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                FirecraftPlayer t = getTarget(player, args[0]);
                String reason = Utils.getReason(1, args);
                if (reason.isEmpty()) {
                    player.sendMessage(Prefixes.ENFORCER + Messages.punishNoReason);
                    return;
                }
                Punishment punishment = Enforcer.createPunishment(t, player, Type.KICK, reason, System.currentTimeMillis(), -1);
                FPacketPunish punish = new FPacketPunish(plugin.getFCServer().getName(), punishment.getId());
                plugin.getSocket().sendPacket(punish);
            }
        }.setBaseRank(Rank.MOD);
        
        FirecraftCommand warn = new FirecraftCommand("warn", "Warns a player on the server") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                FirecraftPlayer t = getTarget(player, args[0]);
                String reason = Utils.getReason(1, args);
                if (reason.isEmpty()) {
                    player.sendMessage(Prefixes.ENFORCER + Messages.punishNoReason);
                    return;
                }
                Punishment punishment = Enforcer.createPunishment(t, player, Type.WARN, reason, System.currentTimeMillis(), -1);
                FPacketPunish punish = new FPacketPunish(plugin.getFCServer().getName(), punishment.getId());
                plugin.getSocket().sendPacket(punish);
            }
        }.setBaseRank(Rank.MOD);
        
        FirecraftCommand unban = new FirecraftCommand("unban", "Removes all active bans from a player") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                handleUnpunishCommand(player, args, "ban");
            }
        }.setBaseRank(Rank.MOD);
        
        FirecraftCommand unmute = new FirecraftCommand("unmute", "Removes all active mutes from a player") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                handleUnpunishCommand(player, args, "mute");
            }
        }.setBaseRank(Rank.MOD);
        
        FirecraftCommand unjail = new FirecraftCommand("unjail", "Removes active jails from a player") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                handleUnpunishCommand(player, args, "jail");
            }
        }.setBaseRank(Rank.MOD);
        
        FirecraftCommand punish = new FirecraftCommand("punish", "Punish a player given a specific rule based on the number of offenses") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (!(args.length >= 2)) {
                    player.sendMessage(Prefixes.ENFORCER + "<ec>Usage: /punish <player> <rule>");
                    return;
                }
                
                FirecraftPlayer target = plugin.getPlayer(args[0]);
                
                Rule rule;
                try {
                    rule = ModeratorRules.getRule(Integer.parseInt(args[1]));
                } catch (NumberFormatException e) {
                    rule = ModeratorRules.getRule(Utils.getReason(1, args));
                }
                
                if (rule == null) {
                    player.sendMessage(Prefixes.ENFORCER + "<ec>You supplied an invalid rule.");
                    return;
                }
                
                RulePunishment rulePunishment = Enforcer.getNextPunishment(player, rule, target);
                Punishment punishment = Enforcer.createPunishment(target, player, System.currentTimeMillis(), rule, rulePunishment);
                if (punishment == null) {
                    player.sendMessage(Prefixes.ENFORCER + "<ec>There was an error creating the punishment.");
                    return;
                }
                
                FPacketPunish packetPunish = new FPacketPunish(plugin.getFCServer().getName(), punishment.getId());
                plugin.getSocket().sendPacket(packetPunish);
            }
        }.setBaseRank(Rank.TRIAL_MOD).addAlias("pu");
        
        FirecraftCommand history = new FirecraftCommand("history", "View the punishment history of a player") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                FirecraftPlayer target = plugin.getPlayerManager().getPlayer(args[0]);
                List<Punishment> punishments = plugin.getFCDatabase().getPunishments(target.getUniqueId());
                if (args.length > 0) {
                    if (args.length == 1) {
                        handlePunishmentList(punishments, player, 1);
                    } else if (args.length == 2) {
                        if (args[0].equalsIgnoreCase("page") || args[0].equalsIgnoreCase("p"))
                            handlePunishmentList(punishments, player, Integer.parseInt(args[1]));
                    } else {
                        player.sendMessage(Prefixes.ENFORCER + "<ec>Invalid arguments.");
                    }
                } else {
                    player.sendMessage(Prefixes.ENFORCER + "<ec>Invalid arguments.");
                }
            }
        }.setBaseRank(Rank.TRIAL_MOD);
        
        FirecraftCommand mrules = new FirecraftCommand("mrules", "Gets a list of moderator rules available.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (args.length == 0) {
                    handleRuleList(player, 1);
                } else if (args.length == 2) {
                    if (args[0].equalsIgnoreCase("page")) {
                        handleRuleList(player, Integer.parseInt(args[1]));
                    }
                }
            }
        }.setBaseRank(Rank.TRIAL_MOD);
        
        plugin.getCommandManager().addCommands(setJail, ban, tempban, mute, tempmute, jail, kick, warn, unban, unmute, unjail, punish, history, mrules);
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
            if (!player.getUniqueId().equals(FirecraftAPI.firestar311)) {
                player.sendMessage(Prefixes.ENFORCER + Messages.noPunishRank);
                return false;
            }
        }
        return true;
    }
    
    private void handlePunishmentList(List<? extends Punishment> punishments, FirecraftPlayer player, int page) {
        PaginatorFactory<Punishment> paginatorFactory = new PaginatorFactory<>();
        paginatorFactory.setMaxElements(7).setHeader("§aPunishment history page {pagenumber} out of {totalpages}").setFooter("§aUse /history page {nextpage} to view the next page.");
        punishments.forEach(paginatorFactory::addElement);
        Paginator<Punishment> paginator = paginatorFactory.build();
        paginator.display(player.getPlayer(), page);
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
        
        FPacketPunish punish = new FPacketPunish(FirecraftAPI.getServer().getId(), punishment.getId());
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
        
        if (target.getMainRank().isEqualToOrHigher(player.getMainRank())) {
            player.sendMessage(Prefixes.ENFORCER + "<ec>You cannot punish someone of equal rank or higher than yours.");
            return;
        }
        
        FPacketPunish punish = new FPacketPunish(FirecraftAPI.getServer().getId(), punishment.getId());
        plugin.getSocket().sendPacket(punish);
    }
    
    private void handleUnpunishCommand(FirecraftPlayer player, String[] args, String type) {
        if (!(args.length == 1)) {
            player.sendMessage(Prefixes.ENFORCER + "<ec>You have an invalid amount of arguments.");
            return;
        }
        
        FirecraftPlayer target = getTarget(player, args[0]);
        List<Punishment> punishments = plugin.getFCDatabase().getPunishments(target.getUniqueId());
        
        for (Punishment punishment : punishments) {
            FirecraftPlayer punisher = plugin.getPlayerManager().getPlayer(punishment.getPunisher());
            if (punisher.getMainRank().equals(Rank.FIRECRAFT_TEAM) && !player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                continue;
            }
            
            if (punishment.isActive()) {
                if (type.equalsIgnoreCase("ban")) {
                    if (!(punishment.getType().equals(Type.BAN) || punishment.getType().equals(Type.TEMP_BAN))) {
                        continue;
                    }
                } else if (type.equalsIgnoreCase("mute")) {
                    if (!(punishment.getType().equals(Type.MUTE) || punishment.getType().equals(Type.TEMP_MUTE))) {
                        continue;
                    }
                } else if (type.equalsIgnoreCase("jail")) {
                    if (!punishment.getType().equals(Type.JAIL)) {
                        continue;
                    }
                }
                
                plugin.getFCDatabase().updateSQL("UPDATE `punishments` SET `active`='false', `removedby`='{remover}' WHERE `id`='{id}';".replace("{remover}", player.getUniqueId().toString()).replace("{id}", punishment.getId() + ""));
                FPacketPunishRemove punishRemove = new FPacketPunishRemove(plugin.getFCServer().getId(), punishment.getId());
                plugin.getSocket().sendPacket(punishRemove);
            }
        }
    }
    
    private void handleRuleList(FirecraftPlayer player, int page) {
        PaginatorFactory<Rule> factory = new PaginatorFactory<>();
        factory.setMaxElements(5).setHeader("§aRules page {pagenumber} out of {totalpages}").setFooter("§aUse /mrules page {nextpage} to view the next page.");
        Collection<Rule> rules = ModeratorRules.getRules().values();
        rules.forEach(factory::addElement);
        Paginator<Rule> paginator = factory.build();
        paginator.display(player.getPlayer(), page);
    }
}