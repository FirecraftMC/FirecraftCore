package net.firecraftmc.core.managers;

import net.firecraftmc.api.FirecraftAPI;
import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Channel;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.interfaces.IPlayerManager;
import net.firecraftmc.api.model.Report;
import net.firecraftmc.api.model.player.ActionBar;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.packets.*;
import net.firecraftmc.api.packets.staffchat.FPStaffChatJoin;
import net.firecraftmc.api.packets.staffchat.FPStaffChatQuit;
import net.firecraftmc.api.toggles.Toggle;
import net.firecraftmc.api.util.Messages;
import net.firecraftmc.api.util.Utils;
import net.firecraftmc.core.FirecraftCore;
import org.apache.commons.lang.StringUtils;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class PlayerManager implements IPlayerManager {
    
    private final ConcurrentHashMap<UUID, FirecraftPlayer> onlinePlayers = new ConcurrentHashMap<>(), cachedPlayers = new ConcurrentHashMap<>();
    private final HashMap<UUID, Long> streamCmdNextUse = new HashMap<>();
    private static final long timeout = TimeUnit.MINUTES.toMillis(10);
    
    private final FirecraftCore plugin;
    
    public PlayerManager(FirecraftCore plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        new BukkitRunnable() {
            public void run() {
                for (FirecraftPlayer p : onlinePlayers.values()) {
                    if (p.getActionBar() != null) p.getActionBar().send(p.getPlayer());
                }
            }
        }.runTaskTimerAsynchronously(plugin, 0L, 1L);
        
        plugin.getSocket().addSocketListener(packet -> {
            if (packet instanceof FPacketRankUpdate) {
                FPacketRankUpdate rankUpdate = (FPacketRankUpdate) packet;
                FirecraftPlayer target = getPlayer(rankUpdate.getTarget());
                FirecraftPlayer updater = getPlayer(rankUpdate.getUpdater());
                if (target != null) {
                    Rank rank = plugin.getFCDatabase().getPlayer(target.getUniqueId()).getMainRank();
                    target.setMainRank(rank);
                    target.sendMessage(Messages.socketRankUpdate);
                    target.updatePlayerListName();
                    String format = Utils.Chat.formatRankUpdate(plugin.getFCServer(packet.getServerId()), updater, target, rankUpdate.getOldRank(), rankUpdate.getNewRank());
                    Utils.Chat.sendStaffChatMessage(getPlayers(), updater, format);
                }
            } else if (packet instanceof FPStaffChatJoin) {
                FPStaffChatJoin staffJoin = ((FPStaffChatJoin) packet);
                FirecraftPlayer staffMember = getPlayer(staffJoin.getPlayer());
                String format = Utils.Chat.formatStaffJoinLeave(plugin.getServerManager().getServer(packet.getServerId()), staffMember, "joined");
                Utils.Chat.sendStaffChatMessage(getPlayers(), staffMember, format);
            } else if (packet instanceof FPStaffChatQuit) {
                FPStaffChatQuit staffQuit = ((FPStaffChatQuit) packet);
                FirecraftPlayer staffMember = getPlayer(staffQuit.getPlayer());
                String format = Utils.Chat.formatStaffJoinLeave(plugin.getServerManager().getServer(packet.getServerId()), staffMember, "left");
                Utils.Chat.sendStaffChatMessage(getPlayers(), staffMember, format);
            }
        });
        
        FirecraftCommand players = new FirecraftCommand("players", "Manage player data.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (!(args.length > 0)) {
                    player.sendMessage(Messages.notEnoughArgs);
                    return;
                }
                
                UUID t;
                try {
                    t = UUID.fromString(args[0]);
                } catch (Exception e) {
                    try {
                        t = Utils.Mojang.getUUIDFromName(args[0]);
                    } catch (Exception e1) {
                        player.sendMessage(Messages.mojangUUIDError);
                        return;
                    }
                }
                
                if (t == null) {
                    player.sendMessage(Messages.mojangUUIDError);
                    return;
                }
                
                FirecraftPlayer target = getPlayer(t);
                if (target == null) target = getCachedPlayer(t);
                if (target == null) target = plugin.getFCDatabase().getPlayer(t);
                if (target == null) {
                    player.sendMessage(Messages.profileError);
                    return;
                }
                
                if (target.getMainRank().isEqualToOrHigher(player.getMainRank())) {
                    if (!player.getUniqueId().equals(FirecraftAPI.firestar311)) {
                        player.sendMessage(Messages.noPermission);
                        return;
                    }
                }
                
                if (Utils.Command.checkCmdAliases(args, 1, "set", "s")) {
                    if (!(args.length == 4)) {
                        player.sendMessage(Messages.notEnoughArgs);
                        return;
                    }
                    
                    if (Utils.Command.checkCmdAliases(args, 2, "mainrank", "mr")) {
                        Rank rank = Rank.getRank(args[3]);
                        if (rank == null) {
                            player.sendMessage("<ec>That is not a valid rank.");
                            return;
                        }
                        
                        if (rank.equals(Rank.FIRECRAFT_TEAM)) {
                            if (!player.getUniqueId().equals(FirecraftAPI.firestar311)) {
                                player.sendMessage("<ec>The Firecraft Team rank can only be updated by Firestar311");
                                return;
                            }
                        }
                        
                        Rank oldRank = target.getMainRank();
                        
                        plugin.getFCDatabase().updateDataColumn(target.getUniqueId(), "mainrank", rank.toString());
                        FPacketRankUpdate rankUpdate = new FPacketRankUpdate(plugin.getFCServer().getId(), player.getUniqueId(), target.getUniqueId(), oldRank, rank);
                        plugin.getSocket().sendPacket(rankUpdate);
                    }
                } else {
                    player.sendMessage("<ec>No other subcommands are currently implemented.");
                }
            }
        };
        players.setBaseRank(Rank.HEAD_ADMIN).addAlias("p");
        
        FirecraftCommand fct = new FirecraftCommand("fct", "Firecraft Team only stuff") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (args.length > 0) {
                    if (Utils.Command.checkCmdAliases(args, 0, "setprefix", "sp")) {
                        String prefix = StringUtils.join(args, " ", 1, args.length);
                        if (plugin.getFCDatabase().setFTPrefix(player.getUniqueId(), prefix)) {
                            player.setFctPrefix(prefix);
                            player.sendMessage(Messages.fct_setPrefix(prefix));
                        } else {
                            player.sendMessage("&cThere was an error setting your prefix.");
                        }
                    } else if (Utils.Command.checkCmdAliases(args, 0, "resetprefix", "rp")) {
                        plugin.getFCDatabase().removeFTPrefix(player.getUniqueId());
                        player.sendMessage(Messages.fct_resetPrefix);
                        player.setFctPrefix(Rank.FIRECRAFT_TEAM.getPrefix());
                    }
                } else {
                    player.sendMessage(Messages.notEnoughArgs);
                }
            }
        };
        fct.setBaseRank(Rank.FIRECRAFT_TEAM);
        
        FirecraftCommand record = new FirecraftCommand("record", "Set yourself to recording mode") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                player.toggle(Toggle.RECORDING);
                if (player.getToggleValue(Toggle.RECORDING)) {
                    player.sendMessage("&bYou have turned on recording mode, this means:");
                    player.sendMessage("&8- &eYou show up as the default rank to other players.");
                    player.sendMessage("&8- &eYou will be able to access all of your rank based perks.");
                    player.sendMessage("&8- &eYou will not receive messages that are for your rank.");
                    player.sendMessage("&8- &eYou will not receive private messages that are from non-staff.");
                    player.setChannel(Channel.GLOBAL);
                    player.setGameMode(GameMode.SURVIVAL);
                    if (player.isNicked()) {
                        player.resetNick(plugin);
                        player.sendMessage("&8- &eYour nickname has been removed.");
                    }
                    if (player.isVanished()) {
                        player.unVanish();
                        for (FirecraftPlayer p : plugin.getPlayerManager().getPlayers()) {
                            p.getPlayer().showPlayer(player.getPlayer());
                            if (!player.isNicked()) {
                                player.getPlayer().setPlayerListName(player.getName());
                            } else {
                                player.getPlayer().setPlayerListName(player.getNick().getProfile().getName());
                            }
                            p.getScoreboard().updateScoreboard(p);
                        }
                        player.setActionBar(null);
                        player.sendMessage("&8- &eYou have been removed from vanish.");
                    }
                    player.updatePlayerListName();
                    player.setActionBar(new ActionBar(Messages.actionBar_Recording));
                } else {
                    player.sendMessage(Messages.recordingModeOff);
                    player.updatePlayerListName();
                    player.setActionBar(null);
                }
            }
        };
        record.setBaseRank(Rank.FAMOUS).setRespectsRecordMode(false);
        
        FirecraftCommand stream = new FirecraftCommand("stream", "Broadast or set your stream url") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (args.length == 0) {
                    if (streamCmdNextUse.containsKey(player.getUniqueId())) {
                        long nextUse = streamCmdNextUse.get(player.getUniqueId());
                        if (!(System.currentTimeMillis() >= nextUse)) {
                            long remaining = nextUse - System.currentTimeMillis();
                            String remainingFormat = Utils.Time.formatTime(remaining);
                            player.sendMessage("<ec>You may use that command again in " + remainingFormat);
                            return;
                        }
                    }
                    
                    String streamUrl = player.getStreamUrl();
                    if (streamUrl == null || streamUrl.equals("")) {
                        player.sendMessage("<ec>You have not set a stream url yet, please use /stream seturl <url>");
                        return;
                    }
                    
                    for (FirecraftPlayer p : onlinePlayers.values()) {
                        p.sendMessage("&e&l" + player.getName() + " &b&lis streaming at &6&l" + streamUrl);
                    }
                    streamCmdNextUse.put(player.getUniqueId(), System.currentTimeMillis() + timeout);
                } else if (args.length > 0) {
                    if (Utils.Command.checkCmdAliases(args, 0, "seturl", "su")) {
                        if (args.length > 0) {
                            player.setStreamUrl(args[1]);
                            player.sendMessage("<nc>You have set your stream url to <vc>" + args[1]);
                        } else {
                            player.sendMessage("<ec>You must provide a url to set.");
                        }
                    } else {
                        player.sendMessage("<ec>Invalid sub command.");
                    }
                }
            }
        };
        stream.setBaseRank(Rank.FAMOUS);
        
        plugin.getCommandManager().addCommands(players, fct, record, stream);
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        e.setJoinMessage(null);
        Player p = e.getPlayer();
        
        FirecraftPlayer player = plugin.getFCDatabase().getPlayer(p.getUniqueId());
        
        if (plugin.getFCServer() != null) {
            FPacketServerPlayerJoin serverPlayerJoin = new FPacketServerPlayerJoin(plugin.getFCServer().getId(), p.getUniqueId());
            plugin.getSocket().sendPacket(serverPlayerJoin);
            plugin.getFCDatabase().updateOnlineStatus(player.getUniqueId(), true, plugin.getFCServer().getName());
            player.setServer(plugin.getFCServer());
            if (Rank.isStaff(player.getMainRank()) || player.getMainRank().equals(Rank.BUILD_TEAM) || player.getMainRank().equals(Rank.VIP) || player.getMainRank().equals(Rank.FAMOUS)) {
                FPStaffChatJoin staffChatJoin = new FPStaffChatJoin(plugin.getFCServer().getId(), player.getUniqueId());
                plugin.getSocket().sendPacket(staffChatJoin);
            } else {
                for (FirecraftPlayer p1 : onlinePlayers.values()) {
                    if (!p1.isIgnoring(player.getUniqueId())) {
                        p1.sendMessage(player.getDisplayName() + " &ajoined the game.");
                    }
                }
            }
        } else {
            player.sendMessage("<ec>&lThe server information is currently not set, please contact a member of The Firecraft Team.");
        }
        
        new BukkitRunnable() {
            public void run() {
                if (Rank.isStaff(player.getMainRank())) {
                    List<Report> reports = plugin.getFCDatabase().getNotClosedReports();
                    
                    if (!reports.isEmpty()) {
                        int unassignedCount = 0, assignedToSelfCount = 0;
                        for (Report report : reports) {
                            if (report.getAssignee() == null) {
                                unassignedCount++;
                            } else {
                                if (report.getAssignee().equals(player.getUniqueId())) {
                                    assignedToSelfCount++;
                                }
                            }
                        }
                        
                        player.sendMessage(Messages.staffReportLogin(reports.size(), unassignedCount, assignedToSelfCount));
                    }
                }
                
                if (!player.getUnseenReportActions().isEmpty()) {
                    for (Integer reportChange : player.getUnseenReportActions()) {
                        String[] arr = plugin.getFCDatabase().getReportChange(reportChange).split(":");
                        if (arr.length == 2) {
                            Report report = plugin.getFCDatabase().getReport(Integer.parseInt(arr[0]));
                            player.sendMessage(Messages.formatReportChange(report, arr[1]));
                        }
                    }
                    player.getUnseenReportActions().clear();
                }
                
                player.loadPlayer();
            }
        }.runTaskLater(plugin, 10L);
        this.onlinePlayers.put(player.getUniqueId(), player);
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        e.setQuitMessage(null);
        FirecraftPlayer player = getPlayer(e.getPlayer().getUniqueId());
        player.refreshOnlineStatus();
        
        if (plugin.getFCServer() != null) {
            if (Rank.isStaff(player.getMainRank()) || player.getMainRank().equals(Rank.BUILD_TEAM) || player.getMainRank().equals(Rank.VIP) || player.getMainRank().equals(Rank.FAMOUS)) {
                FPStaffChatQuit staffQuit = new FPStaffChatQuit(plugin.getFCServer().getId(), player.getUniqueId());
                plugin.getSocket().sendPacket(staffQuit);
            } else {
                for (FirecraftPlayer fp : onlinePlayers.values()) {
                    if (!fp.isIgnoring(player.getUniqueId())) {
                        fp.sendMessage(player.getDisplayName() + " &eleft the game.");
                    }
                }
            }
            
            FPacketServerPlayerLeave playerLeave = new FPacketServerPlayerLeave(plugin.getFCServer().getId(), player.getUniqueId());
            plugin.getSocket().sendPacket(playerLeave);
        }
        
        plugin.getHomeManager().saveHomes(player);
        plugin.getFCDatabase().updateOnlineStatus(player.getUniqueId(), false, "");
        
        onlinePlayers.remove(player.getUniqueId());
        cachedPlayers.put(player.getUniqueId(), player);
        
        if (!onlinePlayers.isEmpty()) {
            for (FirecraftPlayer p : onlinePlayers.values()) {
                p.getScoreboard().updateScoreboard(p);
            }
        }
        long time = System.currentTimeMillis();
        long playTime;
        playTime = player.getLastSeen() == 0 ? time - player.getFirstJoined() : time - player.getLastSeen();
        player.setTimePlayed(player.getTimePlayed() + playTime);
        player.setLastSeen(time);
        player.setOnline(false);
        plugin.getFCDatabase().savePlayer(player);
    }
    
    public FirecraftPlayer getPlayer(UUID uuid) {
        FirecraftPlayer player = onlinePlayers.get(uuid);
        if (player == null) {
            player = cachedPlayers.get(uuid);
        }
        if (player == null) player = plugin.getFCDatabase().getPlayer(uuid);
        return player;
    }
    
    public FirecraftPlayer getPlayer(String name) {
        FirecraftPlayer target = Utils.getPlayer(name, onlinePlayers.values());
        if (target != null) {
            return target;
        }
        
        for (FirecraftPlayer fp : cachedPlayers.values()) {
            if (fp.getName().equalsIgnoreCase(name)) {
                return fp;
            }
        }
        
        UUID uuid = Utils.Mojang.getUUIDFromName(name);
        if (uuid == null) {
            return null;
        }
        return plugin.getFCDatabase().getPlayer(uuid);
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
    
    public void addCachedPlayer(FirecraftPlayer player) {
        this.cachedPlayers.put(player.getUniqueId(), player);
    }
}