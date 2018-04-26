package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.FirecraftPlayer;
import net.firecraftmc.shared.classes.IPlayerManager;
import net.firecraftmc.shared.enums.Channel;
import net.firecraftmc.shared.enums.Rank;
import net.firecraftmc.shared.packets.FPacketServerPlayerJoin;
import net.firecraftmc.shared.packets.FPacketServerPlayerLeave;
import net.firecraftmc.shared.packets.staffchat.FPStaffChatJoin;
import net.firecraftmc.shared.packets.staffchat.FPStaffChatQuit;
import org.bukkit.Bukkit;
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

public class PlayerManager implements IPlayerManager, TabExecutor, Listener {
    private final ConcurrentHashMap<UUID, FirecraftPlayer> onlinePlayers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, FirecraftPlayer> otherProfiles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, FirecraftPlayer> requestedRandomProfiles = new ConcurrentHashMap<>();
    
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
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        e.setJoinMessage(null);
        Player p = e.getPlayer();
        p.sendMessage("§7§oWelcome to FirecraftMC, we have to get a few things before you can do anything.");
        FPacketServerPlayerJoin serverPlayerJoin = new FPacketServerPlayerJoin(plugin.getFirecraftServer(), p.getUniqueId());
        plugin.getSocket().sendPacket(serverPlayerJoin);
        String un = p.getUniqueId().toString().replace("-", "");
        ResultSet set = plugin.getDatabase().querySQL("SELECT * FROM `playerdata` WHERE `uniqueid`='{uuid}'".replace("{uuid}", un));
        FirecraftPlayer player = null;
        try {
            if (set.getFetchSize() == 1) {
                p.sendMessage("§7§oIt appears that you have joined before, getting your data now.");
                set.next();
                Rank rank = Rank.valueOf(set.getString("mainrank"));
                Channel channel = Channel.valueOf(set.getString("channel"));
                boolean vanished = set.getBoolean("vanished");
                boolean inventoryinteract, itempickup, itemuse, blockbreak, blockplace, entityinteract, chatting, silentinventories;
                FirecraftPlayer.VanishInfo vanish = null;
                if (vanished) {
                    inventoryinteract = set.getBoolean("inventoryinteract");
                    itempickup = set.getBoolean("itempickup");
                    itemuse = set.getBoolean("itemuse");
                    blockbreak = set.getBoolean("blockbreak");
                    blockplace = set.getBoolean("blockplace");
                    entityinteract = set.getBoolean("entityinteract");
                    chatting = set.getBoolean("chatting");
                    silentinventories = set.getBoolean("silentinventories");
                    vanish = new FirecraftPlayer.VanishInfo(inventoryinteract, itempickup, itemuse, blockbreak, blockplace, entityinteract, chatting, silentinventories);
                }
                boolean online = set.getBoolean("online");
                
                player = new FirecraftPlayer(p.getUniqueId(), p.getName(), rank, channel, vanish, online);
                plugin.getDatabase().updateSQL("UPDATE `playerdata` SET `online`='true'");
                this.onlinePlayers.put(player.getUniqueId(), player);
                p.sendMessage("§7§oLoad of your data is now complete. You are no longer restricted.");
            } else {
                p.sendMessage("§7§oIt appears that we do not have data for you, creating a default profile.");
                player = new FirecraftPlayer(plugin, p.getUniqueId(), Rank.PRIVATE);
                this.onlinePlayers.put(player.getUniqueId(), player);
                String sql = "INSERT INTO `playerdata`(`uniqueid`, `lastname`, `mainrank`, `channel`, `vanished`, `inventoryinteract`, `itempickup`, `itemuse`, `blockbreak`, `blockplace`, `entityinteract`, `chatting`, `silentinventories`, `online`) VALUES (\"{uuid}\",\"{name}\",\"{rank}\",\"{channel}\",\"{vanished}\",\"{inventoryinteract}\",\"{itempickup}\",\"{itemuse}\",\"{blockbreak}\",\"{blockplace}\",\"{entityinteract}\",\"{chatting}\",\"{silentinventories}\",\"{online}\")";
                sql = sql.replace("{uuid}", player.getUniqueId().toString().replace("-", ""));
                sql = sql.replace("{name}", player.getName());
                sql = sql.replace("{rank}", player.getMainRank().toString());
                sql = sql.replace("{channel}", player.getChannel().toString());
                sql = sql.replace("{vanished}", false + "");
                sql = sql.replace("{inventoryinteract}", false + "");
                sql = sql.replace("{itempickup}", false + "");
                sql = sql.replace("{itemuse}", false + "");
                sql = sql.replace("{blockbreak}", false + "");
                sql = sql.replace("{blockplace}", false + "");
                sql = sql.replace("{entityinteract}", false + "");
                sql = sql.replace("{chatting}", false + "");
                sql = sql.replace("{silentinventories}", false + "");
                sql = sql.replace("{online}", player.isOnline() + "");
                plugin.getDatabase().updateSQL(sql);
                p.sendMessage("§7§oSuccessfully created a profile for you, if you have a special rank, please contact a Firecraft Team member or Head Admin to get that set.");
            }
        } catch (Exception ex) {
            p.kickPlayer("§cThere was an error getting your data. Please contact a Firecraft Team member or Head Admin.");
            ex.printStackTrace();
        }
        
        if (player == null) {
            p.kickPlayer("§cThere was an error getting your data. Please contact a Firecraft Team member or Head Admin.");
            return;
        }
        
        player.playerOnlineStuff();
        if (Rank.isStaff(player.getMainRank()) || player.getMainRank().equals(Rank.BUILD_TEAM) ||
                player.getMainRank().equals(Rank.VIP) || player.getMainRank().equals(Rank.FAMOUS)) {
            FPStaffChatJoin staffChatJoin = new FPStaffChatJoin(plugin.getFirecraftServer(), player);
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
                } //TODO ADD SUPPORT FOR NICKNAMES AS WELL
                
                if (!p.getUniqueId().equals(player.getUniqueId())) {
                    if (p.getPlayer().canSee(player.getPlayer())) {
                        p1.getScoreboard().updateField(FirecraftPlayer.FirecraftScoreboard.SBField.PLAYER_COUNT, "§2" + Bukkit.getOnlinePlayers().size() + "§7/§9" + Bukkit.getServer().getMaxPlayers(), "");
                    }
                }
            }
        }
        
        if (player.isVanished()) {
            for (FirecraftPlayer p1 : onlinePlayers.values()) {
                if (!p1.getMainRank().isEqualToOrHigher(player.getMainRank())) {
                    p1.getPlayer().hidePlayer(player.getPlayer());
                }
            }
        }
    }
    
    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (cmd.getName().equalsIgnoreCase("viewprofile")) {
            if (sender instanceof Player) {
                FirecraftPlayer player = onlinePlayers.get(((Player) sender).getUniqueId());
                if (Rank.isStaff(player.getMainRank())) {
                    if (args.length != 1) {
                        player.sendMessage("&cInvalid amount of arguments.");
                        return true;
                    }
                    UUID uuid;
                    FirecraftPlayer target = null;
                    try {
                        uuid = UUID.fromString(args[0]);
                        target = getPlayer(uuid);
                    } catch (Exception e) {
                        for (FirecraftPlayer p : onlinePlayers.values()) {
                            if (p.getName().equalsIgnoreCase(args[0])) {
                                target = p;
                                break;
                            }
                        }
                    }
                    
                    if (target == null) {
                        player.sendMessage("&cCould not find a player with that name/uuid.");
                        return true;
                    }
                    
                    player.sendMessage("&6Displaying profile info for " + target.getName());
                    String status = (target.getPlayer() != null) ? "&aOnline" : "&cOffline";
                    player.sendMessage("&7Status: " + status);
                    player.sendMessage("&7Rank: " + target.getMainRank().getPrefix());
                    if (!player.getSubRanks().isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        target.getSubRanks().forEach(sr -> sb.append(sr.getPrefix()));
                        player.sendMessage("&7Other Ranks: " + sb.toString());
                    }
                    player.sendMessage("&7Channel: " + target.getChannel().getColor() + target.getChannel().toString());
                    if (target.getNick() != null) {
                        FirecraftPlayer nickProfile = target.getNick().getNickProfile();
                        player.sendMessage("&7Nickname: " + nickProfile.generateDisplayName());
                    }
                }
            }
        }
        return true;
    }
    
    public List<String> onTabComplete(CommandSender sender, Command cmd, String s, String[] args) {
        return null;
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        e.setQuitMessage(null);
        FirecraftPlayer player = getPlayer(e.getPlayer().getUniqueId());
        player.refreshOnlineStatus();
        if (Rank.isStaff(player.getMainRank()) || player.getMainRank().equals(Rank.BUILD_TEAM) ||
                player.getMainRank().equals(Rank.VIP) || player.getMainRank().equals(Rank.FAMOUS)) {
            FPStaffChatQuit staffQuit = new FPStaffChatQuit(plugin.getFirecraftServer(), player);
            plugin.getSocket().sendPacket(staffQuit);
        }
        
        FPacketServerPlayerLeave playerLeave = new FPacketServerPlayerLeave(plugin.getFirecraftServer(), player);
        plugin.getSocket().sendPacket(playerLeave);
        
        onlinePlayers.remove(player.getUniqueId());
        otherProfiles.put(player.getUniqueId(), player);
        
        for (FirecraftPlayer p : onlinePlayers.values()) {
            String online = Bukkit.getServer().getOnlinePlayers().size() - 1 + "";
            String max = Bukkit.getServer().getMaxPlayers() + "";
            p.getScoreboard().updateField(FirecraftPlayer.FirecraftScoreboard.SBField.PLAYER_COUNT, "§2" + online + "§7/§9" + max, "");
        }
    }
    
    public void addPlayer(FirecraftPlayer firecraftPlayer) {
        firecraftPlayer.setPlayer(Bukkit.getPlayer(firecraftPlayer.getUniqueId()));
        this.onlinePlayers.put(firecraftPlayer.getUniqueId(), firecraftPlayer);
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
    
    public void removePlayer(UUID uuid) {
        this.onlinePlayers.remove(uuid);
    }
    
    public void removePlayer(FirecraftPlayer player) {
        removePlayer(player.getUniqueId());
    }
    
    public void addProfile(FirecraftPlayer profile) {
        if (!this.otherProfiles.containsKey(profile.getUniqueId())) {
            this.otherProfiles.put(profile.getUniqueId(), profile);
        } else {
            this.otherProfiles.replace(profile.getUniqueId(), profile);
        }
    }
    
    public FirecraftPlayer getProfile(UUID uuid) {
        if (this.onlinePlayers.containsKey(uuid)) {
            return this.onlinePlayers.get(uuid);
        } else if (this.otherProfiles.containsKey(uuid)) {
            return this.otherProfiles.get(uuid);
        }
        
        return null;
    }
    
    public void updatePlayer(FirecraftPlayer target) {
        target.setPlayer(Bukkit.getPlayer(target.getUniqueId()));
        this.onlinePlayers.replace(target.getUniqueId(), target);
    }
    
    public Collection<FirecraftPlayer> getProfiles() {
        return this.otherProfiles.values();
    }
    
    public void addRandomProfile(UUID requester, FirecraftPlayer profile) {
        if (this.requestedRandomProfiles.containsKey(requester)) {
            this.requestedRandomProfiles.replace(requester, profile);
        } else {
            this.requestedRandomProfiles.put(requester, profile);
        }
    }
    
    public FirecraftPlayer getRandomProfile(UUID requester) {
        return this.requestedRandomProfiles.get(requester);
    }
    
    public FirecraftPlayer getPlayerFromDatabase(UUID uuid) {
        String un = uuid.toString().replace("-", "");
        ResultSet set = plugin.getDatabase().querySQL("SELECT * FROM `playerdata` WHERE `uniqueid`='{uuid}'".replace("{uuid}", un));
        FirecraftPlayer player = null;
        try {
            if (set.getFetchSize() == 1) {
                set.next();
                String lastName = set.getString("lastname");
                Rank rank = Rank.valueOf(set.getString("mainrank"));
                Channel channel = Channel.valueOf(set.getString("channel"));
                boolean vanished = set.getBoolean("vanished");
                boolean inventoryinteract, itempickup, itemuse, blockbreak, blockplace, entityinteract, chatting, silentinventories;
                FirecraftPlayer.VanishInfo vanish = null;
                if (vanished) {
                    inventoryinteract = set.getBoolean("inventoryinteract");
                    itempickup = set.getBoolean("itempickup");
                    itemuse = set.getBoolean("itemuse");
                    blockbreak = set.getBoolean("blockbreak");
                    blockplace = set.getBoolean("blockplace");
                    entityinteract = set.getBoolean("entityinteract");
                    chatting = set.getBoolean("chatting");
                    silentinventories = set.getBoolean("silentinventories");
                    vanish = new FirecraftPlayer.VanishInfo(inventoryinteract, itempickup, itemuse, blockbreak, blockplace, entityinteract, chatting, silentinventories);
                }
                boolean online = set.getBoolean("online");
            
                player = new FirecraftPlayer(uuid, lastName, rank, channel, vanish, online);
                this.otherProfiles.put(player.getUniqueId(), player);
            } else {
                player = new FirecraftPlayer(plugin, uuid, Rank.PRIVATE);
                this.otherProfiles.put(player.getUniqueId(), player);
                String sql = "INSERT INTO `playerdata`(`uniqueid`, `lastname`, `mainrank`, `channel`, `vanished`, `inventoryinteract`, `itempickup`, `itemuse`, `blockbreak`, `blockplace`, `entityinteract`, `chatting`, `silentinventories`, `online`) VALUES (\"{uuid}\",\"{name}\",\"{rank}\",\"{channel}\",\"{vanished}\",\"{inventoryinteract}\",\"{itempickup}\",\"{itemuse}\",\"{blockbreak}\",\"{blockplace}\",\"{entityinteract}\",\"{chatting}\",\"{silentinventories}\",\"{online}\")";
                sql = sql.replace("{uuid}", player.getUniqueId().toString().replace("-", ""));
                sql = sql.replace("{name}", player.getName());
                sql = sql.replace("{rank}", player.getMainRank().toString());
                sql = sql.replace("{channel}", player.getChannel().toString());
                sql = sql.replace("{vanished}", false + "");
                sql = sql.replace("{inventoryinteract}", false + "");
                sql = sql.replace("{itempickup}", false + "");
                sql = sql.replace("{itemuse}", false + "");
                sql = sql.replace("{blockbreak}", false + "");
                sql = sql.replace("{blockplace}", false + "");
                sql = sql.replace("{entityinteract}", false + "");
                sql = sql.replace("{chatting}", false + "");
                sql = sql.replace("{silentinventories}", false + "");
                sql = sql.replace("{online}", player.isOnline() + "");
                plugin.getDatabase().updateSQL(sql);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        
        return player;
    }
}