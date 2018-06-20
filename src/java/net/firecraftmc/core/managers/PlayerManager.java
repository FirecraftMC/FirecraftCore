package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.FirecraftMC;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.classes.enums.Channel;
import net.firecraftmc.shared.classes.enums.Rank;
import net.firecraftmc.shared.classes.interfaces.IPlayerManager;
import net.firecraftmc.shared.classes.model.ActionBar;
import net.firecraftmc.shared.classes.model.Report;
import net.firecraftmc.shared.classes.model.player.FirecraftPlayer;
import net.firecraftmc.shared.classes.model.player.VanishInfo;
import net.firecraftmc.shared.enforcer.punishments.Punishment;
import net.firecraftmc.shared.enforcer.punishments.TemporaryBan;
import net.firecraftmc.shared.packets.FPacketRankUpdate;
import net.firecraftmc.shared.packets.FPacketServerPlayerJoin;
import net.firecraftmc.shared.packets.FPacketServerPlayerLeave;
import net.firecraftmc.shared.packets.staffchat.FPStaffChatJoin;
import net.firecraftmc.shared.packets.staffchat.FPStaffChatQuit;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
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
import java.util.concurrent.TimeUnit;

public class PlayerManager implements IPlayerManager, Listener {

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
                    if (p.getActionBar() != null)
                        p.getActionBar().send(p.getPlayer());
                }
            }
        }.runTaskTimerAsynchronously(plugin, 0L, 1L);

        new BukkitRunnable() {
            public void run() {
                Iterator<UUID> iterator = toKickForPunishment.keySet().iterator();
                while (iterator.hasNext()) {
                    UUID uuid = iterator.next();
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        Punishment punishment = toKickForPunishment.get(uuid);
                        String punisher = plugin.getFCDatabase().getPlayerName(Utils.convertToUUID(punishment.getPunisher()));
                        String reason = punishment.getReason();
                        if (punishment.getType().equals(Punishment.Type.BAN))
                            p.kickPlayer(Utils.color(Messages.banMessage(punisher, reason, "Permanent", punishment.getId())));
                        else if (punishment.getType().equals(Punishment.Type.KICK))
                            p.kickPlayer(Utils.color(Messages.kickMessage(punisher, reason)));
                        else if (punishment instanceof TemporaryBan) {
                            TemporaryBan tempPunishment = ((TemporaryBan) punishment);
                            String expireTime = tempPunishment.formatExpireTime();
                            p.kickPlayer(Utils.color(Messages.banMessage(punisher, reason, expireTime, punishment.getId())));
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
                String format = Utils.Chat.formatStaffJoinLeave(plugin.getFirecraftServer(), staffMember, "joined");
                Utils.Chat.sendStaffChatMessage(getPlayers(), staffMember, format);
            } else if (packet instanceof FPStaffChatQuit) {
                FPStaffChatQuit staffQuit = ((FPStaffChatQuit) packet);
                FirecraftPlayer staffMember = getPlayer(staffQuit.getPlayer());
                String format = Utils.Chat.formatStaffJoinLeave(plugin.getFirecraftServer(), staffMember, "left");
                Utils.Chat.sendStaffChatMessage(getPlayers(), staffMember, format);
            }
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        e.setJoinMessage(null);
        Player p = e.getPlayer();
        FPacketServerPlayerJoin serverPlayerJoin = new FPacketServerPlayerJoin(plugin.getFirecraftServer(), p.getUniqueId());
        plugin.getSocket().sendPacket(serverPlayerJoin);
        FirecraftPlayer player = plugin.getFCDatabase().getPlayer(p.getUniqueId());

        if (player == null) {
            p.kickPlayer(Messages.getDataErrorKick);
            return;
        }

        if (player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
            String prefix = plugin.getFCDatabase().getFTPrefix(player.getUniqueId());
            if (prefix != null) player.setFctPrefix(prefix);
        }

        plugin.getFCDatabase().updateOnlineStatus(player.getUniqueId(), true, plugin.getFirecraftServer().getName());

        player.setServer(plugin.getFirecraftServer());
        if (Rank.isStaff(player.getMainRank()) || player.getMainRank().equals(Rank.BUILD_TEAM) ||
                player.getMainRank().equals(Rank.VIP) || player.getMainRank().equals(Rank.FAMOUS)) {
            FPStaffChatJoin staffChatJoin = new FPStaffChatJoin(plugin.getFirecraftServer(), player.getUniqueId());
            plugin.getSocket().sendPacket(staffChatJoin);
        } else {
            for (FirecraftPlayer p1 : onlinePlayers.values()) {
                if (!p1.isIgnoring(player.getUniqueId())) {
                    p1.sendMessage(player.getDisplayName() + " &ajoined the game.");
                }
            }
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
                fp.sendMessage("\n" + player.getDisplayName() + " &a&lhas joined FirecraftMC for the first time!\n ");
            }
        }
        player.setLastSeen(System.currentTimeMillis());

        new BukkitRunnable() {
            public void run() {
                if (Rank.isStaff(player.getMainRank())) {
                    List<Report> reports = plugin.getFCDatabase().getNotClosedReports();

                    if (reports.size() > 0) {
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
                FirecraftPlayer nick = plugin.getFCDatabase().getNickname(player.getUniqueId());
                if (nick != null) {
                    nick.setSkin(plugin.getFCDatabase().getSkin(nick.getUniqueId()));
                    player.setNick(plugin, nick);
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
            }
        }.runTaskLater(plugin, 10L);
        this.onlinePlayers.put(player.getUniqueId(), player);
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
                if (!fp.isIgnoring(player.getUniqueId())) {
                    fp.sendMessage(player.getDisplayName() + " &eleft the game.");
                }
            }
        }

        FPacketServerPlayerLeave playerLeave = new FPacketServerPlayerLeave(plugin.getFirecraftServer(), player.getUniqueId());
        plugin.getSocket().sendPacket(playerLeave);

        plugin.getHomeManager().saveHomes(player);
        plugin.getFCDatabase().updateOnlineStatus(player.getUniqueId(), false, "");

        onlinePlayers.remove(player.getUniqueId());
        cachedPlayers.put(player.getUniqueId(), player);

        if (onlinePlayers.size() > 0) {
            for (FirecraftPlayer p : onlinePlayers.values()) {
                p.getScoreboard().updateScoreboard(p);
            }
        }
        long time = System.currentTimeMillis();
        long playTime;
        if (player.getLastSeen() == 0) {
            playTime = time - player.getFirstJoined();
        } else {
            playTime = time - player.getLastSeen();
        }
        player.setTimePlayed(player.getTimePlayed() + playTime);
        player.setLastSeen(time);
        player.setOnline(false);
        plugin.getFCDatabase().savePlayer(player);
    }

    /**
     * Gets the FirecraftPlayer given the UUID
     *
     * @param uuid The Unique Id of the player
     * @return The FirecraftPlayer object from memory or the database
     */
    public FirecraftPlayer getPlayer(UUID uuid) {
        FirecraftPlayer player = onlinePlayers.get(uuid);
        if (player == null) {
            player = cachedPlayers.get(uuid);
        }
        if (player == null) player = plugin.getFCDatabase().getPlayer(uuid);
        return player;
    }

    /**
     * Gets the FirecraftPlayer given the name
     *
     * @param name The name of the player
     * @return The FirecraftPlayer object from memory or the database
     */
    public FirecraftPlayer getPlayer(String name) {
        for (FirecraftPlayer fp : onlinePlayers.values()) {
            if (fp.getName().equalsIgnoreCase(name)) {
                return fp;
            }
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

    /**
     * @return List of all online players with the FirecraftPlayer object
     */
    public Collection<FirecraftPlayer> getPlayers() {
        return onlinePlayers.values();
    }

    /**
     * Adds a player to the onlinePlayers list
     *
     * @param player The player to add
     */
    public void addPlayer(FirecraftPlayer player) {
        this.onlinePlayers.put(player.getUniqueId(), player);
    }

    /**
     * Removes a player from the online list
     *
     * @param uuid The Unique ID of the player to remove
     */
    public void removePlayer(UUID uuid) {
        this.onlinePlayers.remove(uuid);
    }

    /**
     * Gets a Player Profile from the cache
     *
     * @param uuid The UUID of the player to get
     * @return The profile of the player
     */
    public FirecraftPlayer getCachedPlayer(UUID uuid) {
        return this.cachedPlayers.get(uuid);
    }

    /**
     * Adds a punishment so that the player can be kicked if they are online, supports cross server kicking/banning
     *
     * @param punishment The Punishment received from the socket
     */
    public void addToKickForPunishment(Punishment punishment) {
        if (!punishment.getServer().equalsIgnoreCase(plugin.getFirecraftServer().getName()))
            this.toKickForPunishment.put(Utils.convertToUUID(punishment.getTarget()), punishment);
    }

    /**
     * Adds a player to the cache, which is used if a player profile is used when not online or they go offline.
     *
     * @param player The player to add to the cache
     */
    public void addCachedPlayer(FirecraftPlayer player) {
        this.cachedPlayers.put(player.getUniqueId(), player);
    }

    /**
     * Add a player to the queue to be teleport to the jail location
     *
     * @param uuid The UUID of the player to be jailed
     */
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

            if (player.isRecording()) {
                player.sendMessage(Messages.recordingNoUse);
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
                    player.sendMessage(Messages.mojangUUIDError);
                    return true;
                }
            }

            if (t == null) {
                player.sendMessage(Messages.mojangUUIDError);
                return true;
            }

            FirecraftPlayer target = getPlayer(t);
            if (target == null) target = getCachedPlayer(t);
            if (target == null)
                target = plugin.getFCDatabase().getPlayer(t);
            if (target == null) {
                player.sendMessage(Messages.profileError);
                return true;
            }

            if (target.getMainRank().isEqualToOrHigher(player.getMainRank())) {
                if (!player.getUniqueId().equals(FirecraftMC.firestar311)) {
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

                        plugin.getFCDatabase().updateDataColumn(target.getUniqueId(), "mainrank", rank.toString());
                        player.sendMessage(Messages.setMainRank(target.getName(), rank));
                        FPacketRankUpdate rankUpdate = new FPacketRankUpdate(plugin.getFirecraftServer(), player.getUniqueId(), target.getUniqueId());
                        plugin.getSocket().sendPacket(rankUpdate);
                    } else if (Utils.Command.checkCmdAliases(args, 2, "channel", "c")) {

                    } else if (Utils.Command.checkCmdAliases(args, 2, "nickname", "nick", "n")) {

                    } else if (Utils.Command.checkCmdAliases(args, 2, "vanish", "v")) {

                    } else if (Utils.Command.checkCmdAliases(args, 2, "firstjoined", "fj")) {

                    } else if (Utils.Command.checkCmdAliases(args, 2, "lastseen", "ls")) {

                    } else if (Utils.Command.checkCmdAliases(args, 2, "god", "g")) {

                    } else if (Utils.Command.checkCmdAliases(args, 2, "socialspy", "ss")) {

                    } else if (Utils.Command.checkCmdAliases(args, 2, "balance", "b")) {

                    } else if (Utils.Command.checkCmdAliases(args, 2, "gamemode", "gm")) {

                    } else if (Utils.Command.checkCmdAliases(args, 2, "streamurl", "su")) {

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
                if (player.isRecording()) {
                    player.sendMessage(Messages.recordingNoUse);
                    return true;
                }

                if (args.length > 0) {
                    if (Utils.Command.checkCmdAliases(args, 0, "setprefix", "sp")) {
                        if (args.length > 2) {
                            player.sendMessage("&cFirecraft Team prefixes cannot have spaces.");
                            return true;
                        }

                        String prefix;
                        if (!player.getUniqueId().equals(FirecraftMC.firestar311)) {
                            prefix = "&4&l" + ChatColor.stripColor(Utils.color(args[1]));
                        } else {
                            prefix = "&4&l" + args[1];
                        }

                        if (plugin.getFCDatabase().setFTPrefix(player.getUniqueId(), prefix)) {
                            player.setFctPrefix(prefix);
                            player.sendMessage(Messages.fct_setPrefix(prefix));
                        } else {
                            player.sendMessage("&cThere was an error setting your prefix.");
                            return true;
                        }
                    } else if (Utils.Command.checkCmdAliases(args, 0, "resetprefix", "rp")) {
                        plugin.getFCDatabase().removeFTPrefix(player.getUniqueId());
                        player.sendMessage(Messages.fct_resetPrefix);
                        player.setFctPrefix(Rank.FIRECRAFT_TEAM.getPrefix());
                        return true;
                    }
                } else {
                    player.sendMessage(Messages.notEnoughArgs);
                    return true;
                }
            } else {
                player.sendMessage(Messages.noPermission);
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("list")) {
            TreeMap<Rank, List<String>> onlinePlayers = new TreeMap<>();
            for (FirecraftPlayer fp : this.onlinePlayers.values()) {
                Rank r = fp.getMainRank();
                if (onlinePlayers.get(r) == null) {
                    onlinePlayers.put(r, new ArrayList<>());
                }

                onlinePlayers.get(r).add(fp.getNameNoPrefix());
            }
            int onlineCount = 0;
            for (FirecraftPlayer fp : this.onlinePlayers.values()) {
                if (fp.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                    if (player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                        onlineCount++;
                    }
                } else {
                    onlineCount++;
                }
            }
            player.sendMessage(Messages.listHeader(onlineCount));
            for (Map.Entry<Rank, List<String>> entry : onlinePlayers.entrySet()) {
                if (entry.getValue().size() != 0) {
                    String line = generateListLine(entry.getKey(), entry.getValue());
                    if (entry.getKey().equals(Rank.FIRECRAFT_TEAM)) {
                        if (player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                            player.sendMessage(line);
                            continue;
                        } else {
                            continue;
                        }
                    }
                    player.sendMessage(line);

                }
            }
        } else if (cmd.getName().equalsIgnoreCase("ignore")) {
            if (!(args.length > 0)) {
                player.sendMessage(Messages.notEnoughArgs);
                return true;
            }

            if (Rank.isStaff(player.getMainRank())) {
                if (!player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                    player.sendMessage("&cStaff members cannot ignored other players.");
                    return true;
                }
            }

            for (String i : args) {
                FirecraftPlayer target = getPlayer(i);
                if (target == null) {
                    player.sendMessage("&cThe name {name} is not valid".replace("{name}", i));
                    continue;
                }

                if (Rank.isStaff(target.getMainRank())) {
                    player.sendMessage("&cYou cannot ignore a staff memnber.");
                    continue;
                }

                player.addIgnored(target.getUniqueId());
                player.sendMessage(Messages.ignoreAction("added", "to", i));
            }
        } else if (cmd.getName().equalsIgnoreCase("unignore")) {
            if (!(args.length > 0)) {
                player.sendMessage(Messages.notEnoughArgs);
                return true;
            }

            for (String i : args) {
                FirecraftPlayer target = getPlayer(i);
                if (target == null) {
                    player.sendMessage("&cThe name {name} is not valid".replace("{name}", i));
                    continue;
                }

                player.removeIgnored(target.getUniqueId());
                player.sendMessage(Messages.ignoreAction("removed", "from", i));
            }
        } else if (cmd.getName().equalsIgnoreCase("record")) {
            if (!player.getMainRank().isEqualToOrHigher(Rank.FAMOUS)) {
                player.sendMessage(Messages.noPermission);
                return true;
            }

            player.setServer(plugin.getFirecraftServer());
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
        } else if (cmd.getName().equalsIgnoreCase("stafflist")) {
            if (!player.getMainRank().isEqualToOrHigher(Rank.HELPER)) {
                player.sendMessage(Messages.noPermission);
                return true;
            }

            HashMap<String, List<FirecraftPlayer>> onlineStaff = plugin.getFCDatabase().getOnlineStaffMembers();

            if (onlineStaff.isEmpty()) {
                player.sendMessage("&cThere was an issue with getting the list of online staff members.");
                return true;
            }

            List<String> displayStrings = new ArrayList<>();
            int serverCount = 0, playerCount = 0;
            for (String server : onlineStaff.keySet()) {
                serverCount++;
                String base = " &8- &7" + server + "&7(&f" + onlineStaff.get(server).size() + "&7): ";
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < onlineStaff.get(server).size(); i++) {
                    FirecraftPlayer fp = onlineStaff.get(server).get(i);
                    if (fp.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                        if (!player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                            continue;
                        }
                    }
                    sb.append(fp.getNameNoPrefix());
                    if (i != onlineStaff.get(server).size() - 1) {
                        sb.append("&7, ");
                    }
                    playerCount++;
                }
                if (!sb.toString().equals("")) {
                    displayStrings.add(base + sb.toString());
                }
            }

            player.sendMessage(Messages.staffListHeader(playerCount, serverCount));
            for (String ss : displayStrings) {
                player.sendMessage(ss);
            }
        } else if (cmd.getName().equalsIgnoreCase("stream")) {
            if (!player.getMainRank().isEqualToOrHigher(Rank.FAMOUS)) {
                player.sendMessage(Messages.noPermission);
                return true;
            }

            if (args.length == 0) {
                if (this.streamCmdNextUse.containsKey(player.getUniqueId())) {
                    long nextUse = streamCmdNextUse.get(player.getUniqueId());
                    if (!(System.currentTimeMillis() >= nextUse)) {
                        long remaining = nextUse - System.currentTimeMillis();
                        String remainingFormat = Utils.Time.formatTime(remaining);
                        player.sendMessage("<ec>You may use that command again in " + remainingFormat);
                        return true;
                    }
                }

                String streamUrl = player.getStreamUrl();
                if (streamUrl == null || streamUrl.equals("")) {
                    player.sendMessage("<ec>You have not set a stream url yet, please use /stream seturl <url>");
                    return true;
                }

                for (FirecraftPlayer p : onlinePlayers.values()) {
                    p.sendMessage("&e&l" + player.getName() + " &b&lis streaming at &6&l" + streamUrl);
                }
                this.streamCmdNextUse.put(player.getUniqueId(), System.currentTimeMillis() + timeout);
            } else if (args.length > 0) {
                if (Utils.Command.checkCmdAliases(args, 0, "seturl", "su")) {
                    if (args.length > 0) {
                        player.setStreamUrl(args[1]);
                        player.sendMessage("<nc>You have set your stream url to <vc>" + args[1]);
                    } else {
                        player.sendMessage("<ec>You must provide a url to set.");
                        return true;
                    }
                } else {
                    player.sendMessage("<ec>Invalid sub command.");
                    return true;
                }
            }
        }
        return true;
    }

    /**
     * Just a shortcut method to prevent repeat code.
     *
     * @param rank    The rank to generate for
     * @param players The players in the rank
     * @return A string representation of the players in that rank that are online.
     */
    private String generateListLine(Rank rank, List<String> players) {
        StringBuilder base = new StringBuilder(" &8- &7" + rank.getTeamName() + " (&f" + players.size() + "&7): ");
        for (int i = 0; i < players.size(); i++) {
            String name = players.get(i);
            base.append(name);
            if (i != players.size() - 1) {
                base.append("&7, ");
            }
        }
        return base.toString();
    }
}