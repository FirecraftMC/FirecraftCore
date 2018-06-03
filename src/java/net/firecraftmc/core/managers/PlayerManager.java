package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.FirecraftMC;
import net.firecraftmc.shared.classes.FirecraftPlayer;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.classes.enums.Rank;
import net.firecraftmc.shared.classes.interfaces.IPlayerManager;
import net.firecraftmc.shared.classes.model.Report;
import net.firecraftmc.shared.enforcer.punishments.Punishment;
import net.firecraftmc.shared.enforcer.punishments.TemporaryBan;
import net.firecraftmc.shared.packets.FPacketRankUpdate;
import net.firecraftmc.shared.packets.FPacketServerPlayerJoin;
import net.firecraftmc.shared.packets.FPacketServerPlayerLeave;
import net.firecraftmc.shared.packets.staffchat.FPStaffChatJoin;
import net.firecraftmc.shared.packets.staffchat.FPStaffChatQuit;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager implements IPlayerManager, Listener {

    private final ConcurrentHashMap<UUID, FirecraftPlayer> onlinePlayers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, FirecraftPlayer> cachedPlayers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Punishment> toKickForPunishment = new ConcurrentHashMap<>();
    private final List<UUID> teleportUnjail = new ArrayList<>();

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
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        e.setJoinMessage(null);
        Player p = e.getPlayer();
        FPacketServerPlayerJoin serverPlayerJoin = new FPacketServerPlayerJoin(plugin.getFirecraftServer(), p.getUniqueId());
        plugin.getSocket().sendPacket(serverPlayerJoin);
        FirecraftPlayer player = plugin.getFCDatabase().getPlayer(plugin.getFirecraftServer(), p.getUniqueId());

        if (player == null) {
            p.kickPlayer(Messages.getDataErrorKick);
            return;
        }

        if (player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
            ResultSet fct = plugin.getFCDatabase().querySQL("SELECT * FROM `fctprefixes` WHERE `fctmember` = '{uuid}';".replace("{uuid}", player.getUniqueId().toString()));
            try {
                if (fct.next()) {
                    player.setFctPrefix(fct.getString("prefix"));
                }
            } catch (Exception ex) {
                player.setFctPrefix(Rank.FIRECRAFT_TEAM.getPrefix());
            }
        }

        this.onlinePlayers.put(player.getUniqueId(), player);
        plugin.getFCDatabase().updateSQL("UPDATE `playerdata` SET `online`='true' WHERE `uniqueid`='" + player.getUniqueId().toString().replace("-", "") + "';");

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
                        p.getPlayer().setPlayerListName(p1.getNick().getProfile().getName() + "§7§l[V]");
                    }

                    if (!player.getMainRank().isEqualToOrHigher(p1.getMainRank())) {
                        player.getPlayer().hidePlayer(p.getPlayer());
                    }
                }

                if (!p.getUniqueId().equals(player.getUniqueId())) {
                    if (p.getPlayer().canSee(player.getPlayer())) {
                        p1.getScoreboard().updateScoreboard(p1);
                    }
                }
            }
        }

        if (player.isNicked()) {
            new BukkitRunnable() {
                public void run() {
                    player.setNick(plugin, player.getNick().getProfile());
                }
            }.runTaskLater(plugin, 10L);
        }

        ResultSet jailSet = plugin.getFCDatabase().querySQL("SELECT * FROM `punishments` WHERE `target`='{uuid}' AND `active`='true' AND `type`='JAIL';".replace("{uuid}", player.getUniqueId().toString().replace("-", "")));
        try {
            if (jailSet.next()) {
                player.teleport(plugin.getJailLocation());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        ResultSet warnSet = plugin.getFCDatabase().querySQL("SELECT * FROM `punishments` WHERE `target`='{uuid}' AND `acknowledged`='false' AND `type`='WARN';".replace("{uuid}", player.getUniqueId().toString().replace("-", "")));
        try {
            if (warnSet.next()) {
                String code = Utils.generateAckCode(Utils.codeCharacters);
                this.plugin.addAckCode(player.getUniqueId(), code);
                player.sendMessage(Messages.joinUnAckWarning(code));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        player.setHomes(plugin.getHomeManager().loadHomes(player.getUniqueId()));

        new BukkitRunnable() {
            public void run() {
                if (Rank.isStaff(player.getMainRank())) {
                    List<Report> reports = new ArrayList<>();
                    ResultSet reportSet = plugin.getFCDatabase().querySQL("SELECT * FROM `reports` WHERE `status` <> 'CLOSED';");
                    try {
                        while (reportSet.next()) {
                            Report report = plugin.getFCDatabase().getReport(reportSet.getInt("id"));
                            reports.add(report);
                        }
                    } catch (Exception ex) {
                    }

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

                        player.sendMessage("&bThere are a total of &e" + reports.size() + " &breports that are not closed.");
                        player.sendMessage("&bThere are a total of &e" + unassignedCount + " &breports that are not assigned.");
                        player.sendMessage("&bThere are a total of &e" + assignedToSelfCount + " &breports that are assigned to you and not closed.");
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
            }
        }.runTaskLater(plugin, 10L);
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

        plugin.getHomeManager().saveHomes(player);
        plugin.getFCDatabase().updateSQL("UPDATE `playerdata` SET `online`='false' WHERE `uniqueid`='" + player.getUniqueId().toString().replace("-", "") + "';");

        onlinePlayers.remove(player.getUniqueId());
        cachedPlayers.put(player.getUniqueId(), player);

        if (onlinePlayers.size() > 0) {
            for (FirecraftPlayer p : onlinePlayers.values()) {
                p.getScoreboard().updateScoreboard(p);
            }
        }
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
        if (player == null) player = cachedPlayers.get(uuid);
        if (player == null) player = plugin.getFCDatabase().getPlayer(plugin.getFirecraftServer(), uuid);
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
        return plugin.getFCDatabase().getPlayer(plugin.getFirecraftServer(), uuid);
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
            if (target == null)
                target = plugin.getFCDatabase().getPlayer(plugin.getFirecraftServer(), t);
            if (target == null) {
                player.sendMessage("&cThere was an error getting the profile of that player.");
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

                        plugin.getFCDatabase().updateSQL("UPDATE `playerdata` SET `mainrank` = '" + rank.toString() + "' WHERE `uniqueid`='{uuid}';".replace("{uuid}", target.getUniqueId().toString().replace("-", "")));
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
                if (args.length > 0) {
                    if (args[0].equalsIgnoreCase("setprefix")) {
                        if (args.length > 2) {
                            player.sendMessage("&cFirecraft Team prefixes cannot have spaces.");
                            return true;
                        }

                        String prefix;
                        if (!player.getUniqueId().equals(FirecraftMC.firestar311)) {
                            prefix = "&4&l" + ChatColor.stripColor(args[1]);
                        } else {
                            prefix = "&4&l" + args[1];
                        }

                        ResultSet set = plugin.getFCDatabase().querySQL("SELECT * FROM `fctprefixes` WHERE `fctmember` = '{uuid}';".replace("{uuid}", player.getUniqueId().toString()));
                        try {
                            String sql;
                            if (set.next()) {
                                sql = "UPDATE `fctprefixes` SET `prefix`='" + prefix + "' WHERE `fctmember` = '{uuid}';".replace("{uuid}", player.getUniqueId().toString());
                            } else {
                                sql = "INSERT INTO `fctprefixes`(`fctmember`, `prefix`) VALUES ('{uuid}','{prefix}');".replace("{uuid}", player.getUniqueId().toString()).replace("{prefix}", prefix);
                            }
                            plugin.getFCDatabase().updateSQL(sql);
                        } catch (Exception e) {
                            player.sendMessage("&cThere was an error setting your prefix.");
                            return true;
                        }
                        player.setFctPrefix(prefix);
                        player.sendMessage("&bYou have set your prefix to " + prefix);
                    } else if (args[0].equalsIgnoreCase("resetprefix")) {
                        plugin.getFCDatabase().updateSQL("DELETE FROM `fctprefixes` WHERE `fctmember`='{uuid}';".replace("{uuid}", player.getUniqueId().toString()));
                        player.sendMessage("&bYou have reset your prefix.");
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
            player.sendMessage("&bThere are a total of &e" + onlineCount + " &bcurrently online.");
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