package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.*;
import net.firecraftmc.shared.enforcer.punishments.Punishment;
import net.firecraftmc.shared.enums.Rank;
import net.firecraftmc.shared.packets.FPacketServerPlayerJoin;
import net.firecraftmc.shared.packets.FPacketServerPlayerLeave;
import net.firecraftmc.shared.packets.staffchat.FPStaffChatJoin;
import net.firecraftmc.shared.packets.staffchat.FPStaffChatQuit;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager implements IPlayerManager, Listener {

    private final ConcurrentHashMap<UUID, FirecraftPlayer> onlinePlayers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, FirecraftPlayer> cachedPlayers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Punishment> toKickForBan = new ConcurrentHashMap<>();

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
                Iterator<UUID> iterator = toKickForBan.keySet().iterator();
                while (iterator.hasNext()) {
                    UUID uuid = iterator.next();
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        Punishment punishment = toKickForBan.get(uuid);
                        String punisher = punishment.getPunisherName();
                        String reason = punishment.getReason();
                        p.kickPlayer(Utils.color("&4&lBANNED\n&fStaff: &c{punisher}\n&fReason: &c{reason}\n&fExpires: &cPermanent".replace("{punisher}", punisher).replace("{reason}", reason)));
                        iterator.remove();
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        e.setJoinMessage(null);
        Player p = e.getPlayer();
        p.sendMessage("§7§oWelcome to FirecraftMC, we have to get a few things before you can do anything.");
        FPacketServerPlayerJoin serverPlayerJoin = new FPacketServerPlayerJoin(plugin.getFirecraftServer(), p.getUniqueId());
        plugin.getSocket().sendPacket(serverPlayerJoin);
        FirecraftPlayer player = Utils.getPlayerFromDatabase(plugin.getDatabase(), plugin, p.getUniqueId());

        if (player == null) {
            p.kickPlayer("§cThere was an error getting your data. Please contact a Firecraft Team member or Head Admin.");
            return;
        }
        
        this.onlinePlayers.put(player.getUniqueId(), player);
        plugin.getDatabase().updateSQL("UPDATE `playerdata` SET `online`='true' WHERE `uniqueid`='" + player.getUniqueId().toString().replace("-", "") + "';");
    
        player.playerOnlineStuff();
        if (Rank.isStaff(player.getMainRank()) || player.getMainRank().equals(Rank.BUILD_TEAM) ||
                player.getMainRank().equals(Rank.VIP) || player.getMainRank().equals(Rank.FAMOUS)) {
            System.out.println("Sending staff chat join packet");
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
    
        player.sendMessage("&7&oSuccessfully loaded your data, you are no longer restricted.");
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
            FPStaffChatQuit staffQuit = new FPStaffChatQuit(plugin.getFirecraftServer(), player.getUniqueId());
            plugin.getSocket().sendPacket(staffQuit);
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
    
    public void addToKickForBan(Punishment punishment) {
        this.toKickForBan.put(Utils.convertToUUID(punishment.getTarget()), punishment);
    }
    
    public void addCachedPlayer(FirecraftPlayer player) {
        this.cachedPlayers.put(player.getUniqueId(), player);
    }
}