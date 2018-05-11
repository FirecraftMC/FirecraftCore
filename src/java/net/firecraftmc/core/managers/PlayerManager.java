package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.*;
import net.firecraftmc.shared.enforcer.Type;
import net.firecraftmc.shared.enforcer.punishments.Punishment;
import net.firecraftmc.shared.enforcer.punishments.TemporaryBan;
import net.firecraftmc.shared.enums.Rank;
import net.firecraftmc.shared.packets.*;
import net.firecraftmc.shared.packets.staffchat.FPStaffChatJoin;
import net.firecraftmc.shared.packets.staffchat.FPStaffChatQuit;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager implements IPlayerManager, Listener, TabExecutor {
    
    private final ConcurrentHashMap<UUID, FirecraftPlayer> onlinePlayers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, FirecraftPlayer> cachedPlayers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Punishment> toKickForPunishment = new ConcurrentHashMap<>();
    private final List<UUID> teleportUnjail = new ArrayList<>();
    private final UUID firestar311 = UUID.fromString("3f7891ce-5a73-4d52-a2ba-299839053fdc");
    
    private final FirecraftCore plugin;
    
    public PlayerManager(FirecraftCore plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        new BukkitRunnable() {
            public void run() {
                for (FirecraftPlayer p : onlinePlayers.values()) {
                    if (p.getActionBar() != null)
                        p.getActionBar().send(p.getPlayer());
                }
            }
        }.runTaskTimerAsynchronously(plugin, 0L, 40L);
        
        new BukkitRunnable() {
            public void run() {
                Iterator<UUID> iterator = toKickForPunishment.keySet().iterator();
                while (iterator.hasNext()) {
                    UUID uuid = iterator.next();
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        Punishment punishment = toKickForPunishment.get(uuid);
                        String punisher = Utils.Database.getPlayerName(plugin.getDatabase(), Utils.convertToUUID(punishment.getPunisher()));
                        String reason = punishment.getReason();
                        if (punishment.getType().equals(Type.BAN))
                            p.kickPlayer(Utils.color(Messages.banMessage(punisher, reason, "Permanent")));
                        else if (punishment.getType().equals(Type.KICK))
                            p.kickPlayer(Utils.color(Messages.kickMessage(punisher, reason)));
                        else if (punishment instanceof TemporaryBan) {
                            TemporaryBan tempPunishment = ((TemporaryBan) punishment);
                            String expireTime = tempPunishment.formatExpireTime();
                            p.kickPlayer(Utils.color(Messages.banMessage(punisher, reason, expireTime)));
                        }
                        iterator.remove();
                    }
                }
                
                ListIterator<UUID> listIterator = teleportUnjail.listIterator();
                while (listIterator.hasNext()) {
                    UUID uuid = listIterator.next();
                    Player p = Bukkit.getPlayer(uuid);
                    p.teleport(plugin.getServerSpawn());
                    listIterator.remove();
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        e.setJoinMessage(null);
        Player p = e.getPlayer();
        p.sendMessage(Utils.color(Messages.welcomeGetData));
        FPacketServerPlayerJoin serverPlayerJoin = new FPacketServerPlayerJoin(plugin.getFirecraftServer(), p.getUniqueId());
        plugin.getSocket().sendPacket(serverPlayerJoin);
        FirecraftPlayer player = Utils.Database.getPlayerFromDatabase(plugin.getFirecraftServer(), plugin.getDatabase(), p.getUniqueId());
        
        if (player == null) {
            p.kickPlayer(Messages.getDataErrorKick);
            return;
        }
        
        if (player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
            ResultSet fct = plugin.getDatabase().querySQL("SELECT * FROM `fctprefixes` WHERE `fctmember` = '{uuid}';".replace("{uuid}", player.getUniqueId().toString()));
            try {
                if (fct.next()) {
                    player.setFctPrefix(fct.getString("prefix"));
                }
            } catch (Exception ex) {
                player.setFctPrefix(Rank.FIRECRAFT_TEAM.getPrefix());
            }
        }
        
        this.onlinePlayers.put(player.getUniqueId(), player);
        plugin.getDatabase().updateSQL("UPDATE `playerdata` SET `online`='true' WHERE `uniqueid`='" + player.getUniqueId().toString().replace("-", "") + "';");
        
        player.playerOnlineStuff();
        if (Rank.isStaff(player.getMainRank()) || player.getMainRank().equals(Rank.BUILD_TEAM) ||
                player.getMainRank().equals(Rank.VIP) || player.getMainRank().equals(Rank.FAMOUS)) {
            FPStaffChatJoin staffChatJoin = new FPStaffChatJoin(plugin.getFirecraftServer(), player.getUniqueId());
            plugin.getSocket().sendPacket(staffChatJoin);
        } else {
            for (FirecraftPlayer p1 : onlinePlayers.values()) {
                p1.sendMessage(player.getDisplayName() + " &ajoined the game.");
            }
        }
        
        for (Player p1 : Bukkit.getOnlinePlayers()) {
            player.getPlayer().hidePlayer(p1);
            player.getPlayer().showPlayer(p1);
        }
        
        if (Bukkit.getOnlinePlayers().size() > 1) {
            for (FirecraftPlayer p1 : onlinePlayers.values()) {
                if (p1.isVanished()) {
                    if (!p1.isNicked()) {
                        p.getPlayer().setPlayerListName(p.getName() + " §7§l[V]");
                    } else {
                        p.getPlayer().setPlayerListName(p1.getNick().getNickProfile().getName() + "§7§l[V]");
                    }
                    
                    if (!player.getMainRank().isEqualToOrHigher(p1.getMainRank())) {
                        player.getPlayer().hidePlayer(p.getPlayer());
                    }
                } //TODO Nicknames should work due to the background code.
                
                if (!p.getUniqueId().equals(player.getUniqueId())) {
                    if (p.getPlayer().canSee(player.getPlayer())) {
                        p1.getScoreboard().updateField(FirecraftPlayer.FirecraftScoreboard.SBField.PLAYER_COUNT, "§2" + Bukkit.getOnlinePlayers().size() + "§7/§9" + Bukkit.getServer().getMaxPlayers(), "");
                    }
                }
            }
        }
        
        if (player.isNicked()) {
            //TODO NOT SUPPORTED YET, PLACEHOLDER
            System.out.println("Player is nicked, this is a placeholder when it is implemented.");
        }
        
        ResultSet jailSet = plugin.getDatabase().querySQL("SELECT * FROM `punishments` WHERE `target`='{uuid}' AND `active`='true' AND `type`='JAIL';".replace("{uuid}", player.getUniqueId().toString().replace("-", "")));
        try {
            if (jailSet.next()) {
                player.teleport(plugin.getJailLocation());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        
        ResultSet warnSet = plugin.getDatabase().querySQL("SELECT * FROM `punishments` WHERE `target`='{uuid}' AND `acknowledged`='false' AND `type`='WARN';".replace("{uuid}", player.getUniqueId().toString().replace("-", "")));
        try {
            if (warnSet.next()) {
                String code = Utils.generateAckCode(Utils.codeCharacters);
                this.plugin.addAckCode(player.getUniqueId(), code);
                player.sendMessage(Messages.joinUnAckWarning(code));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        
        player.sendMessage(Messages.loadDataSuccessful);
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        e.setQuitMessage(null);
        FirecraftPlayer player = getPlayer(e.getPlayer().getUniqueId());
        player.refreshOnlineStatus();
        if (Rank.isStaff(player.getMainRank()) || player.getMainRank().equals(Rank.BUILD_TEAM) ||
                player.getMainRank().equals(Rank.VIP) || player.getMainRank().equals(Rank.FAMOUS)) {
            FPStaffChatQuit staffQuit = new FPStaffChatQuit(plugin.getFirecraftServer(), player.getUniqueId());
            plugin.getSocket().sendPacket(staffQuit);
        } else {
            for (FirecraftPlayer fp : onlinePlayers.values()) {
                fp.sendMessage(player.getDisplayName() + " &eleft the game.");
            }
        }
        
        FPacketServerPlayerLeave playerLeave = new FPacketServerPlayerLeave(plugin.getFirecraftServer(), player.getUniqueId());
        plugin.getSocket().sendPacket(playerLeave);
        
        plugin.getDatabase().updateSQL("UPDATE `playerdata` SET `online`='false' WHERE `uniqueid`='" + player.getUniqueId().toString().replace("-", "") + "';");
        
        onlinePlayers.remove(player.getUniqueId());
        cachedPlayers.put(player.getUniqueId(), player);
        
        if (onlinePlayers.size() > 0) {
            for (FirecraftPlayer p : onlinePlayers.values()) {
                String online = Bukkit.getServer().getOnlinePlayers().size() - 1 + "";
                String max = Bukkit.getServer().getMaxPlayers() + "";
                p.getScoreboard().updateField(FirecraftPlayer.FirecraftScoreboard.SBField.PLAYER_COUNT, "§2" + online + "§7/§9" + max, "");
            }
        }
    }
    
    public FirecraftPlayer getPlayer(UUID uuid) {
        return onlinePlayers.get(uuid);
    }
    
    public FirecraftPlayer getPlayer(String name) {
        for (FirecraftPlayer fp : onlinePlayers.values()) {
            if (fp.getName().equalsIgnoreCase(name)) {
                return fp;
            }
        }
        return null;
    }
    
    public Collection<FirecraftPlayer> getPlayers() {
        return onlinePlayers.values();
    }
    
    public void addPlayer(FirecraftPlayer player) {
        this.onlinePlayers.put(player.getUniqueId(), player);
    }
    
    public void removePlayer(UUID uuid) {
        this.onlinePlayers.remove(uuid);
    }
    
    public FirecraftPlayer getCachedPlayer(UUID uuid) {
        return this.cachedPlayers.get(uuid);
    }
    
    public void addToKickForPunishment(Punishment punishment) {
        if (!punishment.getServer().equalsIgnoreCase(plugin.getFirecraftServer().getName()))
            this.toKickForPunishment.put(Utils.convertToUUID(punishment.getTarget()), punishment);
    }
    
    public void addCachedPlayer(FirecraftPlayer player) {
        this.cachedPlayers.put(player.getUniqueId(), player);
    }
    
    public void addToTeleportUnJail(UUID uuid) {
        this.teleportUnjail.add(uuid);
    }
    
    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Messages.onlyPlayers);
            return true;
        }
        
        UUID sU = ((Player) sender).getUniqueId();
        FirecraftPlayer player = getPlayer(sU);
        
        if (cmd.getName().equals("players")) {
            if (!player.getMainRank().isEqualToOrHigher(Rank.HEAD_ADMIN)) {
                player.sendMessage(Messages.noPermissionPlayerData);
                return true;
            }
    
            if (!(args.length > 0)) {
                player.sendMessage(Messages.notEnoughArgs);
                return true;
            }
    
            UUID t;
            try {
                t = UUID.fromString(args[0]);
            } catch (Exception e) {
                try {
                    t = Utils.Mojang.getUUIDFromName(args[0]);
                } catch (Exception e1) {
                    player.sendMessage("&cThere was an error getting the unique id of that player from Mojang.");
                    return true;
                }
            }
    
            if (t == null) {
                player.sendMessage("&cThere was an error getting the unique id of that player from Mojang.");
                return true;
            }
    
            FirecraftPlayer target = getPlayer(t);
            if (target == null) target = getCachedPlayer(t);
            if (target == null) target = Utils.Database.getPlayerFromDatabase(plugin.getFirecraftServer(), plugin.getDatabase(), t);
            if (target == null) {
                player.sendMessage("&cThere was an error getting the profile of that player.");
                return true;
            }
    
            if (target.getMainRank().isEqualToOrHigher(player.getMainRank())) {
                if (!player.getUniqueId().equals(firestar311)) {
                    player.sendMessage(Messages.noPermission);
                    return true;
                }
            }
    
            if (Utils.Command.checkCmdAliases(args, 1, "set", "s")) {
                if (Utils.Command.checkArgCountExact(sender, args, 4)) {
                    if (Utils.Command.checkCmdAliases(args, 2, "mainrank", "mr")) {
                        Rank rank = Rank.getRank(args[3]);
                        if (rank == null) {
                            player.sendMessage("&cThat is not a valid rank.");
                            return true;
                        }
                
                        if (rank.equals(Rank.FIRECRAFT_TEAM)) {
                            player.sendMessage("&cThe Firecraft Team rank cannot be set in game. Please contact Firestar311 to have it updated.");
                            return true;
                        }
                
                        plugin.getDatabase().updateSQL("UPDATE `playerdata` SET `mainrank` = '" + rank.toString() + "' WHERE `uniqueid`='{uuid}';".replace("{uuid}", target.getUniqueId().toString().replace("-", "")));
                        player.sendMessage(Messages.setMainRank(target.getName(), rank));
                        FPacketRankUpdate rankUpdate = new FPacketRankUpdate(plugin.getFirecraftServer(), player.getUniqueId(), target.getUniqueId());
                        plugin.getSocket().sendPacket(rankUpdate);
                    }
                } else {
                    player.sendMessage(Messages.notEnoughArgs);
                    return true;
                }
            } else {
                player.sendMessage("&cNo other subcommands are currently implemented.");
            }
        } else if (cmd.getName().equalsIgnoreCase("fct")) {
            if (player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                if (args.length > 1) {
                    if (args[0].equalsIgnoreCase("setprefix")) {
                        if (args.length > 2) {
                            player.sendMessage("&cFirecraft Team prefixes cannot have spaces.");
                            return true;
                        }
                        
                        if (args[1].equalsIgnoreCase("reset")) {
                            plugin.getDatabase().updateSQL("DELETE FROM `fctprefixes` WHERE `fctmember`='{uuid}';".replace("{uuid}", player.getUniqueId().toString()));
                            player.sendMessage("&bYou have reset your prefix.");
                            return true;
                        }
                        
                        String prefix;
                        if (!player.getUniqueId().equals(firestar311)) {
                            prefix = "&4&l" + ChatColor.stripColor(args[1]);
                        } else {
                            prefix = "&4&l" + args[1];
                        }
    
                        ResultSet set = plugin.getDatabase().querySQL("SELECT * FROM `fctprefixes` WHERE `fctmember` = '{uuid}';".replace("{uuid}", player.getUniqueId().toString()));
                        try {
                            String sql = "";
                            if (set.next()) {
                                sql = "UPDATE `fctprefixes` SET `prefix`='" + prefix + "' WHERE `fctmember` = '{uuid}';".replace("{uuid}", player.getUniqueId().toString());
                            } else {
                                sql = "INSERT INTO `fctprefixes`(`fctmember`, `prefix`) VALUES ('{uuid}','{prefix}');".replace("{uuid}", player.getUniqueId().toString()).replace("{prefix}", prefix);
                            }
                            plugin.getDatabase().updateSQL(sql);
                        } catch (Exception e) {
                            player.sendMessage("&cThere was an error setting your prefix.");
                            return true;
                        }
                        player.setFctPrefix(prefix);
                        player.sendMessage("&bYou have set your prefix to " + prefix);
                    }
                } else {
                    player.sendMessage(Messages.notEnoughArgs);
                    return true;
                }
            } else {
                player.sendMessage(Messages.noPermission);
                return true;
            }
        }
        return true;
    }
    
    public List<String> onTabComplete(CommandSender sender, Command cmd, String s, String[] args) {
        return null;
    }
}