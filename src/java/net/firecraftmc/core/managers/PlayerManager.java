package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.*;
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
import org.bukkit.scoreboard.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager implements IPlayerManager, TabExecutor, Listener {
    private ConcurrentHashMap<UUID, FirecraftPlayer> onlinePlayers = new ConcurrentHashMap<>();
    private ConcurrentHashMap<UUID, FirecraftPlayer> otherProfiles = new ConcurrentHashMap<>();
    
    private ScoreboardManager scoreboardManager;
    private Map<Rank, Team> teamMap = new HashMap<>();
    
    private FirecraftCore plugin;
    
    public PlayerManager(FirecraftCore plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.scoreboardManager = Bukkit.getScoreboardManager();
        
        for (Rank r : Rank.values()) {
            createScoreboardTeam(r, r.getTeamName());
        }
        
        
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
        p.sendMessage("§7§oPlease wait while we retrieve your data. You will be restricted until we get your data.");
        FPacketServerPlayerJoin serverPlayerJoin = new FPacketServerPlayerJoin(plugin.getFirecraftServer(), p.getUniqueId());
        plugin.getSocket().sendPacket(serverPlayerJoin);
        new BukkitRunnable() {
            public void run() {
                if (getPlayer(p.getUniqueId()) != null) {
                    cancel();
                    p.sendMessage("§7§oYour data has been received, restrictions lifted.");
                    
                    FirecraftPlayer player = getPlayer(p.getUniqueId());
                    player.playerOnlineStuff();
                    if (Rank.isStaff(player.getMainRank())) {
                        FPStaffChatJoin staffChatJoin = new FPStaffChatJoin(plugin.getFirecraftServer(), player);
                        plugin.getSocket().sendPacket(staffChatJoin);
                    }
                    
                    for (FirecraftPlayer p : onlinePlayers.values()) {
                        if (!player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                            p.sendMessage(player.getDisplayName() + " &ajoined the game.");
                        }
                    }
                    
                    Team rankTeam = teamMap.get(player.getMainRank());
                    rankTeam.addEntry(player.getName());
                    
                    
                    for (FirecraftPlayer p : onlinePlayers.values()) {
                        String online = Bukkit.getServer().getOnlinePlayers().size() + "";
                        String max = Bukkit.getServer().getMaxPlayers() + "";
                        p.getScoreboard().updateField(FirecraftPlayer.FirecraftScoreboard.SBField.PLAYER_COUNT, "§2" + online + "§7/§9" + max, "");
                    }
                    
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        player.getPlayer().hidePlayer(p);
                        player.getPlayer().showPlayer(p);
                    }
                    
                    for (FirecraftPlayer p : onlinePlayers.values()) {
                        if (p.isVanished()) {
                            if (!p.getMainRank().equals(player.getMainRank()) || !p.getMainRank().isHigher(player.getMainRank())) {
                                player.getPlayer().hidePlayer(p.getPlayer());
                            }
                        } //TODO ADD SUPPORT FOR NICKNAMES AS WELL
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20);
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
    
    private void createScoreboardTeam(Rank rank, String name) {
        Scoreboard board = scoreboardManager.getMainScoreboard();
        if (board.getTeam(name) == null) {
            Team team = board.registerNewTeam(name);
            if (rank.equals(Rank.BUILD_TEAM)) {
                team.setPrefix(rank.getBaseColor() + "§lBT ");
            } else if (Rank.isStaff(rank)) {
                if (!rank.equals(Rank.FIRECRAFT_TEAM)) {
                    team.setPrefix(rank.getPrefix() + " ");
                } else {
                    team.setPrefix("§4§lFCT " + " ");
                }
            } else {
                team.setPrefix(rank.getPrefix() + " §r");
            }
            this.teamMap.put(rank, team);
        } else {
            this.teamMap.put(rank, board.getTeam(name));
        }
    }
    
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        e.setQuitMessage(null);
        FirecraftPlayer player = getPlayer(e.getPlayer().getUniqueId());
        player.refreshOnlineStatus();
        if (Rank.isStaff(player.getMainRank())) {
            FPStaffChatQuit staffQuit = new FPStaffChatQuit(plugin.getFirecraftServer(), player);
            plugin.getSocket().sendPacket(staffQuit);
        }
        
        FPacketServerPlayerLeave playerLeave = new FPacketServerPlayerLeave(plugin.getFirecraftServer(), player);
        plugin.getSocket().sendPacket(playerLeave);
        
        Team rankTeam = teamMap.get(player.getMainRank());
        rankTeam.removeEntry(player.getName());
        
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
    
}