package net.firecraftmc.core;

import net.firecraftmc.shared.classes.*;
import net.firecraftmc.shared.classes.utils.*;
import net.firecraftmc.shared.enums.Channel;
import net.firecraftmc.shared.enums.Rank;
import net.firecraftmc.shared.exceptions.NicknameException;
import net.firecraftmc.shared.packets.*;
import net.firecraftmc.shared.packets.staffchat.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FirecraftCore extends FirecraftPlugin implements Listener {
    
    private ConcurrentHashMap<UUID, FirecraftPlayer> onlinePlayers = new ConcurrentHashMap<>();
    private ConcurrentHashMap<UUID, FirecraftPlayer> otherProfiles = new ConcurrentHashMap<>();
    
    private List<UUID> settingNick = new ArrayList<>();
    private Map<UUID, FirecraftPlayer> confirmNick = new HashMap<>();
    
    private NickWrapper nickWrapper;
    
    private FirecraftSocket socket;
    private FirecraftServer server;
    
    private ScoreboardManager scoreboardManager;
    private Map<Rank, Team> teamMap = new HashMap<>();
    
    public void onEnable() {
        instance = this;
        this.saveDefaultConfig();
        this.socket = new FirecraftSocket(this, "127.0.0.1", getConfig().getInt("port"));
        this.socket.start();
        this.getServer().getPluginManager().registerEvents(this, this);
        this.server = new FirecraftServer(getConfig().getString("server.name"), ChatColor.valueOf(getConfig().getString("server.color")));
        
        String versionString = ReflectionUtils.getVersion();
        if (versionString.equalsIgnoreCase("v1_8_R3")) {
            this.nickWrapper = new NickWrapper1_8_R3();
        } else if (versionString.equalsIgnoreCase("v1_12_R1")) {
            this.nickWrapper = new NickWrapper1_12_R1();
        }
        
        this.scoreboardManager = Bukkit.getScoreboardManager();
        
        for (Rank r : Rank.values()) {
            createScoreboardTeam(r, r.getTeamName());
        }
        
        new BukkitRunnable() {
            public void run() {
                if (socket == null || !socket.isOpen()) {
                    socket = new FirecraftSocket(instance, "127.0.0.1", getConfig().getInt("port"));
                }
            }
        }.runTaskTimerAsynchronously(this, 5 * 60L, 60L);
    }
    
    public void onDisable() {
        if (socket != null) {
            socket.sendPacket(new FPacketServerDisconnect(server));
            socket.close();
        }
    }
    
    public NickWrapper getNickWrapper() {
        return nickWrapper;
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        e.setJoinMessage(null);
        Player p = e.getPlayer();
        p.sendMessage("§7§oPlease wait while we retrieve your data. You will be restricted until we get your data.");
        FPacketServerPlayerJoin serverPlayerJoin = new FPacketServerPlayerJoin(server, p.getUniqueId());
        socket.sendPacket(serverPlayerJoin);
        new BukkitRunnable() {
            public void run() {
                if (getFirecraftPlayer(p.getUniqueId()) != null) {
                    cancel();
                    p.sendMessage("§7§oYour data has been received, restrictions lifted.");
                    
                    FirecraftPlayer player = getFirecraftPlayer(p.getUniqueId());
                    if (Rank.isStaff(player.getMainRank())) {
                        FPStaffChatJoin staffChatJoin = new FPStaffChatJoin(server, player);
                        socket.sendPacket(staffChatJoin);
                    }
                    
                    for (FirecraftPlayer p : onlinePlayers.values()) {
                        if (!player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                            p.sendMessage(player.getDisplayName() + " &ajoined the game.");
                        }
                    }
                    
                    Team rankTeam = teamMap.get(player.getMainRank());
                    rankTeam.addEntry(player.getName());
    
                    player.playerOnlineStuff();
                    
                    for (FirecraftPlayer p : onlinePlayers.values()) {
                        String online = Bukkit.getServer().getOnlinePlayers().size() + "";
                        String max = Bukkit.getServer().getMaxPlayers() + "";
                        p.getScoreboard().updateField(FirecraftPlayer.FirecraftScoreboard.SBField.PLAYER_COUNT, "§2" + online + "§7/§9" + max, "");
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20);
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        e.setQuitMessage(null);
        FirecraftPlayer player = getFirecraftPlayer(e.getPlayer().getUniqueId());
        if (Rank.isStaff(player.getMainRank())) {
            FPStaffChatQuit staffQuit = new FPStaffChatQuit(server, player);
            socket.sendPacket(staffQuit);
        }
        
        Team rankTeam = teamMap.get(player.getMainRank());
        rankTeam.removeEntry(player.getName());
        
        onlinePlayers.remove(player.getUuid());
        otherProfiles.put(player.getUuid(), player);
    
        for (FirecraftPlayer p : onlinePlayers.values()) {
            String online = Bukkit.getServer().getOnlinePlayers().size()-1 + "";
            String max = Bukkit.getServer().getMaxPlayers() + "";
            p.getScoreboard().updateField(FirecraftPlayer.FirecraftScoreboard.SBField.PLAYER_COUNT, "§2" + online + "§7/§9" + max, "");
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChat(AsyncPlayerChatEvent e) {
        e.setCancelled(true);
        FirecraftPlayer player = getFirecraftPlayer(e.getPlayer().getUniqueId());
        if (player == null) {
            e.getPlayer().sendMessage("§cYour player data has not been received yet, you are not allowed to speak.");
            return;
        }
        
        if (player.getChannel().equals(Channel.GLOBAL)) {
            String format = ChatUtils.formatGlobal(player, e.getMessage());
            for (FirecraftPlayer op : onlinePlayers.values()) {
                op.sendMessage(format);
            }
        } else if (player.getChannel().equals(Channel.STAFF)) {
            FPStaffChatMessage msg = new FPStaffChatMessage(server, player, e.getMessage());
            socket.sendPacket(msg);
        }
    }
    
    public void addFirecraftPlayer(FirecraftPlayer firecraftPlayer) {
        firecraftPlayer.setPlayer(Bukkit.getPlayer(firecraftPlayer.getUuid()));
        this.onlinePlayers.put(firecraftPlayer.getUuid(), firecraftPlayer);
    }
    
    public FirecraftPlayer getFirecraftPlayer(UUID uuid) {
        return onlinePlayers.get(uuid);
    }
    
    public Collection<FirecraftPlayer> getFirecraftPlayers() {
        return onlinePlayers.values();
    }
    
    public void removeFirecraftPlayer(UUID uuid) {
        this.onlinePlayers.remove(uuid);
    }
    
    public void addProfile(FirecraftPlayer profile) {
        this.otherProfiles.put(profile.getUuid(), profile);
    }
    
    public FirecraftPlayer getProfile(UUID uuid) {
        if (this.onlinePlayers.containsKey(uuid)) {
            return this.onlinePlayers.get(uuid);
        } else if (this.otherProfiles.containsKey(uuid)) {
            return this.otherProfiles.get(uuid);
        }
        
        return null;
    }
    
    public FirecraftSocket getSocket() {
        return socket;
    }
    
    public void updateFirecraftPlayer(FirecraftPlayer target) {
        target.setPlayer(Bukkit.getPlayer(target.getUuid()));
        this.onlinePlayers.replace(target.getUuid(), target);
    }
    
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("chat")) {
            if (sender instanceof ConsoleCommandSender) {
                sender.sendMessage("§cConsole cannot use the chat command.");
                return true;
            } else if (sender instanceof Player) {
                FirecraftPlayer player = getFirecraftPlayer(((Player) sender).getUniqueId());
                
                if (!Utils.checkFirecraftPlayer((Player) sender, player)) return true;
                if (!CmdUtils.checkArgCountExact(sender, args, 1)) return true;
                if (CmdUtils.checkCmdAliases(args, 0, "staff", "st", "s")) {
                    if (!Rank.isStaff(player.getMainRank())) {
                        player.sendMessage("&cOnly staff members may use the staff chat channel.");
                        return true;
                    }
                    
                    if (player.getChannel().equals(Channel.STAFF)) {
                        player.sendMessage("&cYou are already speaking in that channel.");
                        return true;
                    }
                    player.setChannel(Channel.STAFF);
                    player.sendMessage("&aYou are now speaking in " + Channel.STAFF.getColor() + "&lSTAFF");
                } else if (CmdUtils.checkCmdAliases(args, 0, "global", "gl", "g")) {
                    if (player.getChannel().equals(Channel.GLOBAL)) {
                        player.sendMessage("&cYou are already speaking in that channel.");
                        return true;
                    }
                    player.setChannel(Channel.GLOBAL);
                    player.sendMessage("&aYou are now speaking in " + Channel.GLOBAL.getColor() + "&lGLOBAL");
                } else {
                    player.sendMessage("&cSupport for other channels is currently not implemented.");
                    return true;
                }
            }
        } else if (cmd.getName().equalsIgnoreCase("gamemode")) {
            if (sender instanceof Player) {
                if (!CmdUtils.checkArgCountGreater(sender, args, 0)) return true;
                
                FirecraftPlayer player = getFirecraftPlayer(((Player) sender).getUniqueId());
                if (!Utils.checkFirecraftPlayer((Player) sender, player)) return true;
                
                if (player.getMainRank().equals(Rank.TRIAL_ADMIN) || player.getMainRank().isHigher(Rank.TRIAL_ADMIN)) {
                    GameMode mode = null;
                    if (CmdUtils.checkCmdAliases(args, 0, "creative", "c", "1")) {
                        mode = GameMode.CREATIVE;
                    } else if (CmdUtils.checkCmdAliases(args, 0, "survival", "s", "0")) {
                        mode = GameMode.SURVIVAL;
                    } else if (CmdUtils.checkCmdAliases(args, 0, "adventure", "a", "2")) {
                        mode = GameMode.ADVENTURE;
                    } else if (CmdUtils.checkCmdAliases(args, 0, "spectator", "sp", "spec", "3")) {
                        mode = GameMode.SPECTATOR;
                    }
                    
                    if (mode == null) {
                        player.sendMessage("&cYou did not provide a valid gamemode.");
                        return true;
                    }
                    
                    FirecraftPlayer target = null;
                    if (args.length > 1) {
                        for (FirecraftPlayer p : getFirecraftPlayers()) {
                            if (p.getName().equalsIgnoreCase(args[1])) {
                                target = p;
                            }
                        }
                    }
                    
                    if (target != null) {
                        if (player.getMainRank().equals(Rank.TRIAL_ADMIN)) {
                            player.sendMessage("&cOnly Admins and Higher can set other player's gamemodes.");
                            return true;
                        }
                        
                        //TODO Add a bypass for FCT Members
                        if (target.getMainRank().equals(player.getMainRank()) || target.getMainRank().isHigher(player.getMainRank())) {
                            player.sendMessage("&cYou cannot set the gamemode of someone of the same rank or higher than you.");
                            return true;
                        }
                        
                        target.setGamemode(mode);
                        player.sendMessage("&aYou have set &b" + target.getDisplayName() + "&a's gamemode to &b" + mode.toString().toLowerCase());
                        target.sendMessage("&aYour gamemode has been set to &b" + mode.toString().toLowerCase() + " &aby &b" + player.getDisplayName());
                        return true;
                    }
                    
                    player.setGamemode(mode);
                    player.sendMessage("&aYou set your own gamemode to &b" + mode.toString().toLowerCase());
                } else {
                    player.sendMessage("&cOnly Junior Admins and above can use the gamemode command.");
                    return true;
                }
            } else if (sender instanceof ConsoleCommandSender) {
                sender.sendMessage("§cIt is not yet implemented for console to set gamemodes.");
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("gmc")) {
            gamemodeShortcut(sender, GameMode.CREATIVE, args);
        } else if (cmd.getName().equalsIgnoreCase("gms")) {
            gamemodeShortcut(sender, GameMode.SURVIVAL, args);
        } else if (cmd.getName().equalsIgnoreCase("gmsp")) {
            gamemodeShortcut(sender, GameMode.SPECTATOR, args);
        } else if (cmd.getName().equalsIgnoreCase("gma")) {
            gamemodeShortcut(sender, GameMode.ADVENTURE, args);
        } else if (cmd.getName().equalsIgnoreCase("teleport")) {
            if (sender instanceof ConsoleCommandSender) {
                sender.sendMessage("§cConsole is not able to teleport players.");
                return true;
            }
            
            FirecraftPlayer player = getFirecraftPlayer(((Player) sender).getUniqueId());
            if (!Utils.checkFirecraftPlayer((Player) sender, player)) return true;
            
            if (!(player.getMainRank().equals(Rank.TRIAL_ADMIN) || player.getMainRank().isHigher(Rank.TRIAL_ADMIN))) {
                //TODO Add checks for staff based ranks for SrMods and below
                player.sendMessage("&cOnly Junior Mods and above can teleport directly.");
                return true;
            }
            
            if (args.length == 1) {
                FirecraftPlayer target = null;
                for (FirecraftPlayer fp : getFirecraftPlayers()) {
                    if (fp.getName().equalsIgnoreCase(args[0])) {
                        target = fp;
                    }
                }
                
                if (target == null) {
                    player.sendMessage("&cCould not find the player " + args[0]);
                    return true;
                }
                
                if (player.getMainRank().equals(Rank.TRIAL_ADMIN)) {
                    if (target.getMainRank().isHigher(Rank.TRIAL_ADMIN)) {
                        player.sendMessage("&cJunior Mods cannot teleport to players of higher rank.");
                        return true;
                    }
                }
                
                player.teleport(target.getLocation());
                player.sendMessage("&aYou teleported to " + target.getDisplayName());
                if (target.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                    target.sendMessage(player.getName() + " &ateleported to you.");
                    player.sendMessage("&7&oYou teleported to a Firecraft Team member, they were notified of that action.");
                }
            } else if (args.length == 2) {
                if (player.getMainRank().equals(Rank.TRIAL_ADMIN) || player.getMainRank().equals(Rank.MOD)) {
                    player.sendMessage("&cOnly Junior Admins and above can teleport players to other players.");
                    return true;
                }
                
                FirecraftPlayer t1 = null;
                FirecraftPlayer t2 = null;
                
                for (FirecraftPlayer fp : getFirecraftPlayers()) {
                    if (fp.getName().equalsIgnoreCase(args[0])) {
                        t1 = fp;
                    } else if (fp.getName().equalsIgnoreCase(args[1])) {
                        t2 = fp;
                    }
                }
                
                if (t1 == null) {
                    player.sendMessage("&cThe name provided for the first player is invalid.");
                    return true;
                }
                
                if (t2 == null) {
                    player.sendMessage("&cThe name provided for the second player is invalid.");
                    return true;
                }
                
                if (t1.getMainRank().equals(player.getMainRank()) || t1.getMainRank().isHigher(player.getMainRank())) {
                    player.sendMessage("&cYou cannot forcefully teleport players equal to or higher than your rank.");
                    return true;
                }
                
                t1.teleport(t2.getLocation());
                t1.sendMessage("&aYou were teleported to " + t2.getDisplayName() + " &aby " + player.getDisplayName());
                t2.sendMessage(t1.getDisplayName() + " &awas teleported to you by " + player.getDisplayName());
                player.sendMessage("&aYou teleported " + t1.getDisplayName() + " &ato " + t2.getDisplayName());
            } else {
                player.sendMessage("&cYou did not provide the correct number of arguments.");
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("nick")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cOnly players may use that command.");
                return true;
            }
            
            FirecraftPlayer player = getFirecraftPlayer(((Player) sender).getUniqueId());
            if (!Utils.checkFirecraftPlayer((Player) sender, player)) return true;
            if (!CmdUtils.checkArgCountExact(sender, args, 1)) return true;
            
            if (!(player.getMainRank().equals(Rank.VIP) || player.getMainRank().equals(Rank.TRIAL_ADMIN) || player.getMainRank().isHigher(Rank.TRIAL_ADMIN))) {
                player.sendMessage("&cYou are not allowed to use the nickname command.");
                return true;
            }
            this.settingNick.add(player.getUuid());
            player.sendMessage("&6&l╔═══════════════════════════════");
            player.sendMessage("&6&l║ &7You have started the nickname process.");
            player.sendMessage("&6&l║ &7You may cancel with &c/nickcancel&7.");
            player.sendMessage("&6&l║ &7You will be restricted until finished or until canceled.");
            player.sendMessage("&6&l║ &7Getting the UUID of the name provided.");
            UUID uuid;
            try {
                uuid = MojangUtils.getUUIDFromName(args[0]);
            } catch (Exception e) {
                player.sendMessage("&6&l║ &cThere was an error getting the UUID from the name, nickname process cancelled.");
                player.sendMessage("&6&l╚═══════════════════════════════");
                this.settingNick.remove(player.getUuid());
                return true;
            }
            
            if (getFirecraftPlayer(uuid) != null) {
                player.sendMessage("&6&l║ &7The profile that you requested is online on this server. You cannot use it.");
                player.sendMessage("&6&l╚═══════════════════════════════");
                settingNick.remove(player.getUuid());
                return true;
            }
            
            if (getProfile(uuid) == null) {
                player.sendMessage("&6&l║ &7There is no local copy for the nickname provided.");
                player.sendMessage("&6&l║ &7Requesting the profile for " + args[0] + " this may take a bit.");
                
                FPRequestProfile profileRequest = new FPRequestProfile(uuid);
                this.socket.sendPacket(profileRequest);
                new BukkitRunnable() {
                    public void run() {
                        FirecraftPlayer profile = getProfile(uuid);
                        if (profile != null) {
                            cancel();
                            if (profile.getMainRank().isHigher(player.getMainRank()) || profile.getMainRank().equals(player.getMainRank())) {
                                player.sendMessage("&6&l║ &7The profile's rank is equal to or higher than yours. You cannot use it.");
                                player.sendMessage("&6&l╚═══════════════════════════════");
                                settingNick.remove(player.getUuid());
                                return;
                            }
                            
                            if (profile.isOnline()) {
                                player.sendMessage("&6&l║ &7The profile that you requested is online on a different server. You cannot use it.");
                                player.sendMessage("&6&l╚═══════════════════════════════");
                                settingNick.remove(player.getUuid());
                                return;
                            }
                            
                            confirmNick.put(player.getUuid(), profile);
                            player.sendMessage("&6&l║ &7The profile has been received, you need to confirm the request.");
                            player.sendMessage("&6&l║ &7To confirm type &a/nickconfirm&7. To cancel type &c/nickcancel&7.");
                            player.sendMessage("&6&l╚═══════════════════════════════");
                        }
                    }
                }.runTaskTimerAsynchronously(this, 0L, 10L);
            } else {
                player.sendMessage("&6&l║ &7There is information for the nickname you provided.");
                player.sendMessage("&6&l║ &7You must confirm the nickname information.");
                player.sendMessage("&6&l║ &7To confirm type &a/nickconfirm &7tocancel type &c/nickcancel&7.");
                player.sendMessage("&6&l╚═══════════════════════════════");
                this.confirmNick.put(player.getUuid(), getProfile(uuid));
            }
        } else if (cmd.getName().equalsIgnoreCase("nickcancel")) {
            if (sender instanceof Player) {
                FirecraftPlayer player = getFirecraftPlayer(((Player) sender).getUniqueId());
                if (this.confirmNick.containsKey(player.getUuid()) || this.settingNick.contains(player.getUuid())) {
                    player.sendMessage("&cYou have cancelled the nickname process.");
                    this.confirmNick.remove(player.getUuid());
                    this.settingNick.remove(player.getUuid());
                    return true;
                } else {
                    player.sendMessage("&cYou are not currently setting a nickname.");
                    return true;
                }
            } else {
                sender.sendMessage("§cOnly players may use that command.");
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("nickconfirm")) {
            if (sender instanceof Player) {
                FirecraftPlayer player = getFirecraftPlayer(((Player) sender).getUniqueId());
                if (this.confirmNick.containsKey(player.getUuid())) {
                    FirecraftPlayer nick = this.confirmNick.get(player.getUuid());
                    try {
                        player.setNick(this, nick);
                    } catch (NicknameException e) {
                        player.sendMessage("&cThere was an error setting the nickname.");
                        this.settingNick.remove(player.getUuid());
                        this.confirmNick.remove(player.getUuid());
                        return true;
                    }
                    
                    player.sendMessage("&aSet your nickname to &b" + nick.getName());
                    this.settingNick.remove(player.getUuid());
                    this.confirmNick.remove(player.getUuid());
                    player.setActionBar(new ActionBar("&fYou are currently &cNICKED"));
                    FPStaffChatSetNick setNick = new FPStaffChatSetNick(server, player, nick);
                } else {
                    player.sendMessage("&cYou are not currently setting a nickname.");
                    return true;
                }
            } else {
                sender.sendMessage("§cOnly players may use that command.");
                return true;
            }
            
        } else if (cmd.getName().equalsIgnoreCase("nickreset")) {
            if (sender instanceof ConsoleCommandSender) {
                sender.sendMessage("§cConsole can't set nicknames, so it can't reset nicknames");
                return true;
            }
            
            FirecraftPlayer player = getFirecraftPlayer(((Player) sender).getUniqueId());
            if (player.getNick() == null) {
                player.sendMessage("&cYou do not have a nick currently set.");
                return true;
            }
            
            try {
                player.resetNick(this);
            } catch (NicknameException e) {
                player.sendMessage("&cThere was an error resetting your nickname.");
                return true;
            }
            
            player.sendMessage("&aYou have reset your nickname.");
        } else if (cmd.getName().equalsIgnoreCase("vanish")) {
            sender.sendMessage("§cNot implemented yet.");
        } else if (cmd.getName().equalsIgnoreCase("viewprofile")) {
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
                        target = getFirecraftPlayer(uuid);
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
    
    private void gamemodeShortcut(CommandSender sender, GameMode mode, String[] args) {
        if (sender instanceof Player) {
            FirecraftPlayer player = getFirecraftPlayer(((Player) sender).getUniqueId());
            if (player.getMainRank().equals(Rank.TRIAL_ADMIN) || player.getMainRank().isHigher(Rank.TRIAL_ADMIN)) {
                FirecraftPlayer target = null;
                if (args.length > 0) {
                    for (FirecraftPlayer p : getFirecraftPlayers()) {
                        if (p.getName().equalsIgnoreCase(args[1])) {
                            target = p;
                        }
                    }
                }
                
                if (target != null) {
                    if (player.getMainRank().equals(Rank.TRIAL_ADMIN)) {
                        player.sendMessage("&cOnly Admins and Higher can set other player's gamemodes.");
                        return;
                    }
                    
                    //TODO Add a bypass for FCT Members
                    if (target.getMainRank().equals(player.getMainRank()) || target.getMainRank().isHigher(player.getMainRank())) {
                        player.sendMessage("&cYou cannot set the gamemode of someone of the same rank or higher than you.");
                        return;
                    }
                    
                    target.setGamemode(mode);
                    player.sendMessage("&aYou have set " + target.getDisplayName() + "&a's gamemode to &b" + mode.toString().toLowerCase());
                    target.sendMessage("&aYour gamemode has been set to &b" + mode.toString().toLowerCase() + " &aby " + player.getDisplayName());
                    return;
                }
                
                player.setGamemode(mode);
                player.sendMessage("&aYou set your own gamemode to &b" + mode.toString().toLowerCase());
            } else {
                player.sendMessage("&cOnly Junior Admins and above can use the gamemode command.");
            }
        } else if (sender instanceof ConsoleCommandSender) {
            sender.sendMessage("§cIt is not yet implemented for console to set gamemodes.");
        }
    }
    
    private void createScoreboardTeam(Rank rank, String name) {
        Scoreboard board = scoreboardManager.getMainScoreboard();
        if (board.getTeam(name) == null) {
            Team team = board.registerNewTeam(name);
            if (rank.equals(Rank.BUILD_TEAM)) {
                team.setPrefix(rank.getBaseColor() + "§lBT ");
            } else if (Rank.isStaff(rank)) {
                team.setPrefix(rank.getPrefix() + " ");
            } else {
                team.setPrefix(rank.getPrefix() + " §r");
            }
            this.teamMap.put(rank, team);
        } else {
            this.teamMap.put(rank, board.getTeam(name));
        }
    }
    
    public FirecraftServer getFirecraftServer() {
        return server;
    }
}