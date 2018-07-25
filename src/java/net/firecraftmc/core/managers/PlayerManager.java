package net.firecraftmc.core.managers;

import net.firecraftmc.api.FirecraftAPI;
import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Channel;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.interfaces.IPlayerManager;
import net.firecraftmc.api.model.Report;
import net.firecraftmc.api.model.Transaction;
import net.firecraftmc.api.model.player.*;
import net.firecraftmc.api.packets.*;
import net.firecraftmc.api.packets.staffchat.FPStaffChatJoin;
import net.firecraftmc.api.packets.staffchat.FPStaffChatQuit;
import net.firecraftmc.api.punishments.Punishment;
import net.firecraftmc.api.punishments.Punishment.Type;
import net.firecraftmc.api.punishments.TemporaryPunishment;
import net.firecraftmc.api.util.Messages;
import net.firecraftmc.api.util.Utils;
import net.firecraftmc.core.FirecraftCore;
import org.apache.commons.lang.StringUtils;
import org.bukkit.*;
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
    private final ConcurrentHashMap<UUID, Punishment> toKickForPunishment = new ConcurrentHashMap<>();
    private final List<UUID> teleportUnjail = new ArrayList<>();
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
        
        new BukkitRunnable() {
            public void run() {
                Iterator<UUID> iterator = toKickForPunishment.keySet().iterator();
                while (iterator.hasNext()) {
                    System.out.println("Found a punishment");
                    UUID uuid = iterator.next();
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        Punishment punishment = toKickForPunishment.get(uuid);
                        System.out.println(punishment.getType());
                        if (punishment.getType().equals(Type.BAN))
                            p.kickPlayer(Utils.color(Messages.banMessage(punishment, "Permanent")));
                        else if (punishment.getType().equals(Type.KICK))
                            p.kickPlayer(Utils.color(Messages.kickMessage(punishment.getPunisherName(), punishment.getReason())));
                        else if (punishment.getType().equals(Type.TEMP_BAN)) {
                            TemporaryPunishment tempPunishment = ((TemporaryPunishment) punishment);
                            String expireTime = tempPunishment.formatExpireTime();
                            p.kickPlayer(Utils.color(Messages.banMessage(punishment, expireTime)));
                        }
                        iterator.remove();
                    }
                }
                
                ListIterator<UUID> listIterator = teleportUnjail.listIterator();
                while (listIterator.hasNext()) {
                    UUID uuid = listIterator.next();
                    Player p = Bukkit.getPlayer(uuid);
                    p.teleport(plugin.getSpawn());
                    listIterator.remove();
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
        
        plugin.getSocket().addSocketListener(packet -> {
            if (packet instanceof FPacketRankUpdate) {
                FPacketRankUpdate rankUpdate = (FPacketRankUpdate) packet;
                FirecraftPlayer player = getPlayer(rankUpdate.getTarget());
                if (player != null) {
                    Rank rank = plugin.getFCDatabase().getPlayer(player.getUniqueId()).getMainRank();
                    player.setMainRank(rank);
                    player.sendMessage(Messages.socketRankUpdate);
                    player.updatePlayerListName();
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
                            player.sendMessage("&cThat is not a valid rank.");
                            return;
                        }
                        
                        if (rank.equals(Rank.FIRECRAFT_TEAM)) {
                            player.sendMessage("&cThe Firecraft Team rank cannot be set in game. Please contact Firestar311 to have it updated.");
                            return;
                        }
                        
                        plugin.getFCDatabase().updateDataColumn(target.getUniqueId(), "mainrank", rank.toString());
                        player.sendMessage(Messages.setMainRank(target.getName(), rank));
                        FPacketRankUpdate rankUpdate = new FPacketRankUpdate(plugin.getFCServer().getId(), player.getUniqueId(), target.getUniqueId());
                        plugin.getSocket().sendPacket(rankUpdate);
                    }
                } else {
                    player.sendMessage("&cNo other subcommands are currently implemented.");
                }
            }
        };
        players.setBaseRank(Rank.HEAD_ADMIN).addAlias("p");
        
        FirecraftCommand fct = new FirecraftCommand("fct", "Firecraft Team only stuff") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (args.length > 0) {
                    if (Utils.Command.checkCmdAliases(args, 0, "setprefix", "sp")) {
                        String prefix = StringUtils.join(args, " ", 1, args.length);
                        if (!player.getUniqueId().equals(FirecraftAPI.firestar311)) {
                            prefix = ChatColor.stripColor(prefix);
                        }
                        
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
                player.setRecording(!player.isRecording());
                if (player.isRecording()) {
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
        record.setBaseRank(Rank.FAMOUS);
        
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
        
        if (player == null) {
            p.kickPlayer(Messages.getDataErrorKick);
            return;
        }
        
        if (player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
            String prefix = plugin.getFCDatabase().getFTPrefix(player.getUniqueId());
            if (prefix != null) player.setFctPrefix(prefix);
        }
        
        if (plugin.getFCDatabase().hasActiveJail(player.getUniqueId())) {
            player.teleport(plugin.getJailLocation());
        }
        
        if (plugin.getFCDatabase().hasUnacknowledgedWarnings(player.getUniqueId())) {
            String code = Utils.generateAckCode(Utils.codeCharacters);
            this.plugin.addAckCode(player.getUniqueId(), code);
            player.sendMessage(Messages.joinUnAckWarning(code));
        }
        
        player.setHomes(plugin.getHomeManager().loadHomes(player.getUniqueId()));
        
        if (player.getFirstJoined() == 0) {
            player.setFirstJoined(System.currentTimeMillis());
            for (FirecraftPlayer fp : onlinePlayers.values()) {
                fp.sendMessage("\n" + player.getDisplayName() + " &a&lhas joined FirecraftAPI for the first time!\n ");
            }
        }
        player.setLastSeen(System.currentTimeMillis());
        
        List<Transaction> transactions = plugin.getFCDatabase().getTransactions(player.getUniqueId());
        transactions.forEach(transaction -> player.getProfile().addTransaction(transaction));
        
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
                
                player.playerOnlineStuff();
                NickInfo nick = plugin.getFCDatabase().getNickname(player.getUniqueId());
                if (nick != null) {
                    nick.getProfile().setSkin(plugin.getFCDatabase().getSkin(nick.getProfile().getUniqueId()));
                    player.setNick(plugin, nick.getProfile(), nick.getRank());
                }
                
                if (player.getVanishInfo() != null) {
                    VanishInfo info = player.getVanishInfo();
                    player.vanish();
                    player.setVanishInfo(info);
                    for (FirecraftPlayer p : plugin.getPlayerManager().getPlayers()) {
                        if (!player.isNicked()) {
                            player.getPlayer().setPlayerListName(player.getName() + " §7§l[V]");
                        } else {
                            player.getPlayer().setPlayerListName(player.getNick().getProfile().getName() + "§7§l[V]");
                        }
                        
                        if (!p.getMainRank().isEqualToOrHigher(player.getMainRank())) {
                            p.getPlayer().hidePlayer(player.getPlayer());
                        }
                    }
                    
                    player.setActionBar(new ActionBar(Messages.actionBar_Vanished));
                    player.getVanishInfo().setAllowFlightBeforeVanish(player.getPlayer().getAllowFlight());
                    player.getPlayer().setAllowFlight(true);
                }
                player.updatePlayerListName();
                
                for (FirecraftPlayer p : onlinePlayers.values()) {
                    if (p.isVanished()) {
                        if (!player.getMainRank().isEqualToOrHigher(p.getMainRank())) {
                            player.getPlayer().hidePlayer(p.getPlayer());
                        }
                    }
                }
                player.getScoreboard().updateScoreboard(player);
                
                List<Mail> mail = plugin.getFCDatabase().getMailByReceiver(player.getUniqueId());
                int unreadAmount = 0;
                for (Mail m : mail) {
                    if (!m.isRead()) unreadAmount++;
                }
                
                if (unreadAmount != 0) {
                    player.sendMessage("<nc>You have <vc>" + unreadAmount + " <nc>unread mail messages.");
                }
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
    
    public void addToKickForPunishment(Punishment punishment) {
        this.toKickForPunishment.put(punishment.getTarget(), punishment);
    }
    
    public void addCachedPlayer(FirecraftPlayer player) {
        this.cachedPlayers.put(player.getUniqueId(), player);
    }
    
    public void addToTeleportUnJail(UUID uuid) {
        this.teleportUnjail.add(uuid);
    }
}