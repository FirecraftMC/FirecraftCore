package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.*;
import net.firecraftmc.shared.enforcer.Enforcer;
import net.firecraftmc.shared.enforcer.Type;
import net.firecraftmc.shared.enforcer.punishments.*;
import net.firecraftmc.shared.enums.Rank;
import net.firecraftmc.shared.packets.FPacketPunish;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

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
        try {
            while (set.next()) {
                if (set.getBoolean("active")) {
                    Type type = Type.valueOf(set.getString("type"));
                    if (type.equals(Type.BAN) || type.equals(Type.TEMP_BAN)) {
                        long expire = set.getLong("expire");
                        String expireDiff = Utils.Time.formatTime(expire - System.currentTimeMillis());
                        FirecraftPlayer punisher = Utils.getPlayerFromDatabase(plugin.getFirecraftServer(), plugin.getDatabase(), plugin, Utils.convertToUUID(set.getString("punisher")));
                        e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Utils.color("&4&lBANNED\n&fStaff: &c{punisher}\n&fReason: &c{reason}\n&fExpires: &c{expire}".replace("{punisher}", punisher.getName()).replace("{reason}", set.getString("reason")).replace("{expire}", (expire == 0) ? "Permanent" : expireDiff)));
                    }
                }
            }
        } catch (SQLException e1) {
            e1.printStackTrace();
        }
    }
    
    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (sender instanceof Player) {
            FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
            if (cmd.getName().equalsIgnoreCase("setjail")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                    player.sendMessage(prefix + "&cOnly Admins+ can set the jail location.");
                    return true;
                }
    
                plugin.setJailLocation(player.getLocation());
                player.sendMessage(prefix + "&aYou have set the jail location to your current location.");
                return true;
            }
            
            //TODO The direct commands will only be available to Admin+ when the automation part is implemented.
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
                player.sendMessage(prefix + "&cThe name/uuid you provided is not valid.");
                return true;
            }
            
            FirecraftPlayer t = plugin.getPlayerManager().getPlayer(uuid);
            if (t == null) {
                t = Utils.getPlayerFromDatabase(plugin.getFirecraftServer(), plugin.getDatabase(), plugin, uuid);
                if (t == null) {
                    player.sendMessage(prefix + "&cCould not find a profile by that name, currently only players that have joined can be punished.");
                    return true;
                }
            }
            
            if (t.getMainRank().isEqualToOrHigher(player.getMainRank())) {
                if (!player.getUniqueId().equals(firestar311)) {
                    player.sendMessage(prefix + "&cYou cannot punish a player that is equal to or higher than your rank.");
                    return true;
                }
            }
            
            if (!(args.length > 1)) {
                player.sendMessage(prefix + "&cYou must provide a reason for the punishment.");
                return true;
            }
            
            long date = System.currentTimeMillis();
            String punisher = player.getUniqueId().toString().replace("-", "");
            String target = t.getUniqueId().toString().replace("-", "");
            FirecraftServer server = plugin.getFirecraftServer();
            if (cmd.getName().equalsIgnoreCase("ban")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                    player.sendMessage(prefix + "&cOnly Admins+ can permanently ban a player.");
                    return true;
                }
                
                Type type = Type.BAN;
                
                String reason = getReason(1, args);
                
                PermanentBan permBan = new PermanentBan(type, server.getName(), punisher, target, reason, date);
                permBan.setActive(true);
                Punishment permanentBan = Enforcer.addToDatabase(plugin.getDatabase(), permBan);
                if (Bukkit.getPlayer(t.getUniqueId()) != null)
                    t.kickPlayer("&4&lBANNED\n&fStaff: &c{punisher}\n&fReason: &c{reason}\n&fExpires: &cPermanent".replace("{punisher}", player.getName()).replace("{reason}", reason));
                if (permanentBan != null) {
                    FPacketPunish punish = new FPacketPunish(server, permanentBan.getId());
                    plugin.getSocket().sendPacket(punish);
                } else {
                    player.sendMessage(prefix + "&cThere was an issue creating the punishment.");
                    return true;
                }
            } else if (cmd.getName().equalsIgnoreCase("tempban")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.MOD)) {
                    player.sendMessage(prefix + "&cOnly Mods+ can tempban a player.");
                    return true;
                }
                
                String ti = "PT" + args[1].toUpperCase();
                long expire = Duration.parse(ti).toMillis();
                long expireDate = date + expire;
                String reason = getReason(2, args);
                Type type = Type.TEMP_BAN;
                TemporaryBan tempBan = new TemporaryBan(type, server.getName(), punisher, target, reason, date, expireDate);
                tempBan.setActive(true);
                TemporaryPunishment temporaryBan = (TemporaryPunishment) Enforcer.addToDatabase(plugin.getDatabase(), tempBan);
                if (temporaryBan == null) {
                    player.sendMessage(prefix + "&cThere was an error creating that punishment.");
                    return true;
                }
                if (Bukkit.getPlayer(t.getUniqueId()) != null) {
                    t.kickPlayer("&4&lBANNED\n&fStaff: &c{punisher}\n&fReason: &c{reason}\n&fExpires: &c{expire}".replace("{punisher}", player.getName()).replace("{reason}", reason).replace("{expire}", temporaryBan.formatExpireTime()));
                }
                FPacketPunish punish = new FPacketPunish(server, temporaryBan.getId());
                plugin.getSocket().sendPacket(punish);
            } else if (cmd.getName().equalsIgnoreCase("ipban")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.HEAD_ADMIN)) {
                    player.sendMessage(prefix + "&cOnly Head Admins+ can IP-ban a player.");
                    return true;
                }
            } else if (cmd.getName().equalsIgnoreCase("mute")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.MOD)) {
                    player.sendMessage(prefix + "&cOnly Mods+ can permanently mute a player.");
                    return true;
                }
    
                Type type = Type.MUTE;
    
                String reason = getReason(1, args);
    
                PermanentMute permMute = new PermanentMute(type, server.getName(), punisher, target, reason, date);
                permMute.setActive(true);
                Punishment permanentMute = Enforcer.addToDatabase(plugin.getDatabase(), permMute);
                if (permanentMute != null) {
                    FPacketPunish punish = new FPacketPunish(server, permanentMute.getId());
                    plugin.getSocket().sendPacket(punish);
                } else {
                    player.sendMessage(prefix + "&cThere was an issue creating the punishment.");
                    return true;
                }
                if (Bukkit.getPlayer(t.getUniqueId()) != null)
                    t.sendMessage("&cYou have been permanently muted by " + player.getName());
            } else if (cmd.getName().equalsIgnoreCase("tempmute")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.HELPER)) {
                    player.sendMessage(prefix + "&cOnly Helpers+ can tempmute a player.");
                    return true;
                }
    
                String ti = "PT" + args[1].toUpperCase();
                long expire = Duration.parse(ti).toMillis();
                long expireDate = date + expire;
                String reason = getReason(2, args);
                Type type = Type.TEMP_MUTE;
                TemporaryBan tempBan = new TemporaryBan(type, server.getName(), punisher, target, reason, date, expireDate);
                tempBan.setActive(true);
                TemporaryPunishment temporaryMute = (TemporaryPunishment) Enforcer.addToDatabase(plugin.getDatabase(), tempBan);
                if (temporaryMute == null) {
                    player.sendMessage(prefix + "&cThere was an error creating that punishment.");
                    return true;
                }
                if (Bukkit.getPlayer(t.getUniqueId()) != null) {
                    t.sendMessage("&cYou have been temporarily muted by " + player.getName());
                }
                FPacketPunish punish = new FPacketPunish(server, temporaryMute.getId());
                plugin.getSocket().sendPacket(punish);
            } else if (cmd.getName().equalsIgnoreCase("jail")) {
                if (!player.getMainRank().equals(Rank.HELPER)) {
                    player.sendMessage(prefix + "&cOnly Helpers can jail a player. If you are a Mod+, use the actual punishment.");
                    return true;
                }
    
                Location jailLocation = plugin.getJailLocation();
                if (jailLocation == null) {
                    player.sendMessage(prefix + "&cThe jail location is currently not set, please contact an Admin+");
                    return true;
                }
    
                String reason = getReason(1, args);
                Jail j = new Jail(server.getName(), punisher, target, reason, date);
                j.setActive(true);
                Punishment jail = Enforcer.addToDatabase(plugin.getDatabase(), j);
                if (jail != null) {
                    FPacketPunish punish = new FPacketPunish(server, jail.getId());
                    plugin.getSocket().sendPacket(punish);
                } else {
                    player.sendMessage(prefix + "&cThere was an issue creating the punishment.");
                    return true;
                }
                if (Bukkit.getPlayer(t.getUniqueId()) != null) {
                    t.sendMessage("&cYou have been jailed by " + player.getName());
                    t.teleport(jailLocation);
                }
            } else if (cmd.getName().equalsIgnoreCase("kick")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.HELPER)) {
                    player.sendMessage(prefix + "&cOnly Helpers+ can kick a player.");
                    return true;
                }
                
                Type type = Type.KICK;
                String reason = getReason(1, args);
                Kick k = new Kick(type, server.getName(), punisher, target, reason, date);
                
                Kick kick = (Kick) Enforcer.addToDatabase(plugin.getDatabase(), k);
                if (Bukkit.getPlayer(t.getUniqueId()) != null)
                    t.kickPlayer("&a&lKICKED\n&fStaff: &c{punisher}\n&fReason: &c{reason}\n".replace("{punisher}", player.getName()).replace("{reason}", reason));
                if (kick != null) {
                    FPacketPunish punish = new FPacketPunish(server, kick.getId());
                    plugin.getSocket().sendPacket(punish);
                } else {
                    player.sendMessage(prefix + "&cThere was an issue creating the punishment.");
                    return true;
                }
            } else if (cmd.getName().equalsIgnoreCase("warn")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.HELPER)) {
                    player.sendMessage(prefix + "&cOnly Helpers+ can warn a player.");
                    return true;
                }
    
                String reason = getReason(1, args);
                Warn w = new Warn(server.getName(), punisher, target, reason, date);
                w.setActive(true);
                Warn warn = (Warn) Enforcer.addToDatabase(plugin.getDatabase(), w);
                if (warn != null) {
                    FPacketPunish punish = new FPacketPunish(server, warn.getId());
                    plugin.getSocket().sendPacket(punish);
                } else {
                    player.sendMessage(prefix + "&cThere was an issue creating the punishment.");
                    return true;
                }
                if (Bukkit.getPlayer(t.getUniqueId()) != null) {
                    String code = Utils.generateAckCode(Utils.codeCharacters);
                    plugin.addAckCode(t.getUniqueId(), code);
                    t.sendMessage("&cYou have been warned by " + player.getName() + " for " + warn.getReason());
                    t.sendMessage("&cYou must acknowledge this by typing in the code: " + code);
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