package net.firecraftmc.core;

import net.firecraftmc.shared.classes.*;
import net.firecraftmc.shared.enums.Channel;
import net.firecraftmc.shared.enums.Rank;
import net.firecraftmc.shared.exceptions.NicknameException;
import net.firecraftmc.shared.packets.FPRequestProfile;
import net.firecraftmc.shared.packets.FPacketServerDisconnect;
import net.firecraftmc.shared.packets.FPacketServerPlayerJoin;
import net.firecraftmc.shared.packets.staffchat.FPStaffChatJoin;
import net.firecraftmc.shared.packets.staffchat.FPStaffChatMessage;
import net.firecraftmc.shared.packets.staffchat.FPStaffChatQuit;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FirecraftCore extends FirecraftPlugin implements Listener {

    private ConcurrentHashMap<UUID, FirecraftPlayer> onlineFirecraftPlayers = new ConcurrentHashMap<>();
    private ConcurrentHashMap<UUID, FirecraftPlayer> requestedProfiles = new ConcurrentHashMap<>();

    private List<UUID> settingNick = new ArrayList<>();
    private Map<UUID, FirecraftPlayer> confirmNick = new HashMap<>();

    private FirecraftSocket socket;
    private FirecraftServer server;

    public void onEnable() {
        this.saveDefaultConfig();
        this.socket = new FirecraftSocket(this, "localhost", getConfig().getInt("port"));
        this.socket.start();
        this.getServer().getPluginManager().registerEvents(this, this);
        this.server = new FirecraftServer(getConfig().getString("server.name"), ChatColor.valueOf(getConfig().getString("server.color")));
    }

    public void onDisable() { socket.sendPacket(new FPacketServerDisconnect(server)); }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        e.setJoinMessage(null);
        Player p = e.getPlayer();
        p.sendMessage("§7§oPlease wait while we retreive your data...");
        FPacketServerPlayerJoin serverPlayerJoin = new FPacketServerPlayerJoin(server, p.getUniqueId());
        socket.sendPacket(serverPlayerJoin);
        System.out.println("Sent the Player Join Packet.");
        new BukkitRunnable() {
            public void run() {
                if (getFirecraftPlayer(p.getUniqueId()) != null) {
                    cancel();
                    p.sendMessage("§7§oYour data has been received, restrictions lifted.");

                    FirecraftPlayer player = getFirecraftPlayer(p.getUniqueId());
                    if (Rank.isStaff(player.getRank())) {
                        FPStaffChatJoin staffChatJoin = new FPStaffChatJoin(server, player);
                        socket.sendPacket(staffChatJoin);
                    }
                }
            }
        }.runTaskTimerAsynchronously(this, 0L, 20);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        e.setQuitMessage(null);
        FirecraftPlayer player = getFirecraftPlayer(e.getPlayer().getUniqueId());
        if (Rank.isStaff(player.getRank())) {
            FPStaffChatQuit staffQuit = new FPStaffChatQuit(server, player);
            socket.sendPacket(staffQuit);
        }

        onlineFirecraftPlayers.remove(player.getUuid());
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent e) {
        e.setCancelled(true);
        FirecraftPlayer player = getFirecraftPlayer(e.getPlayer().getUniqueId());
        if (player == null) {
            e.getPlayer().sendMessage("§cYour player data has not been received yet, you are not alowed to speak.");
            return;
        }

        if (player.getChannel().equals(Channel.GLOBAL)) {
            String format = ChatUtils.formatGlobal(player, e.getMessage());
            for (FirecraftPlayer op : onlineFirecraftPlayers.values()) {
                op.sendMessage(format);
            }
        } else if (player.getChannel().equals(Channel.STAFF)) {
            FPStaffChatMessage msg = new FPStaffChatMessage(server, player, e.getMessage());
            socket.sendPacket(msg);
        }
    }

    public void addFirecraftPlayer(FirecraftPlayer firecraftPlayer) {
        firecraftPlayer.setPlayer(Bukkit.getPlayer(firecraftPlayer.getUuid()));
        this.onlineFirecraftPlayers.put(firecraftPlayer.getUuid(), firecraftPlayer);
    }

    public FirecraftPlayer getFirecraftPlayer(UUID uuid) { return onlineFirecraftPlayers.get(uuid); }

    public Collection<FirecraftPlayer> getFirecraftPlayers() { return onlineFirecraftPlayers.values(); }

    public void removeFirecraftPlayer(UUID uuid) { this.onlineFirecraftPlayers.remove(uuid); }

    public void addProfile(FirecraftPlayer profile) { this.requestedProfiles.put(profile.getUuid(), profile); }

    public FirecraftPlayer getProfile(UUID uuid) {
        if (this.onlineFirecraftPlayers.containsKey(uuid)) {
            return this.onlineFirecraftPlayers.get(uuid);
        } else if (this.requestedProfiles.containsKey(uuid)) {
            return this.requestedProfiles.get(uuid);
        }

        return null;
    }

    public FirecraftSocket getSocket() { return socket; }

    public void updateFirecraftPlayer(FirecraftPlayer target) {
        target.setPlayer(Bukkit.getPlayer(target.getUuid()));
        this.onlineFirecraftPlayers.replace(target.getUuid(), target);
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
                    if (!Rank.isStaff(player.getRank())) {
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

                if (player.getRank().equals(Rank.JUNIOR_ADMIN) || player.getRank().isHigher(Rank.JUNIOR_ADMIN)) {
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
                        if (player.getRank().equals(Rank.JUNIOR_ADMIN)) {
                            player.sendMessage("&cOnly Admins and Higher can set other player's gamemodes.");
                            return true;
                        }

                        //TODO Add a bypass for FCT Members
                        if (target.getRank().equals(player.getRank()) || target.getRank().isHigher(player.getRank())) {
                            player.sendMessage("&cYou cannot set the gamemode of someone of the same rank or higher than you.");
                            return true;
                        }

                        target.setGamemode(mode);
                        player.sendMessage("&aYou have set " + target.getNameNoPrefix() + "&a's gamemode to &b" + mode.toString().toLowerCase());
                        target.sendMessage("&aYour gamemode has been set to &b" + mode.toString().toLowerCase() + " &aby " + player.getNameNoPrefix());
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

            if (!(player.getRank().equals(Rank.JUNIOR_MOD) || player.getRank().isHigher(Rank.JUNIOR_MOD))) {
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

                if (player.getRank().equals(Rank.JUNIOR_MOD)) {
                    if (target.getRank().isHigher(Rank.JUNIOR_MOD)) {
                        player.sendMessage("&cJunior Mods cannot teleport to players of higher rank.");
                        return true;
                    }
                }

                player.teleport(target.getLocation());
                player.sendMessage("&aYou teleported to " + target.getNameNoPrefix());
                if (target.getRank().equals(Rank.FIRECRAFT_TEAM)) {
                    target.sendMessage(player.getNameNoPrefix() + " &ateleported to you.");
                    player.sendMessage("&7&oYou teleported to a Firecraft Team member, they were notified of that action.");
                }
            } else if (args.length == 2) {
                if (player.getRank().equals(Rank.JUNIOR_MOD) || player.getRank().equals(Rank.MOD) || player.getRank().equals(Rank.SENIOR_MOD)) {
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

                if (t1.getRank().equals(player.getRank()) || t1.getRank().isHigher(player.getRank())) {
                    player.sendMessage("&cYou cannot forcefully teleport players equal to or higher than your rank.");
                    return true;
                }

                t1.teleport(t2.getLocation());
                t1.sendMessage("&aYou were teleported to " + t2.getNameNoPrefix() + " &aby " + player.getNameNoPrefix());
                t2.sendMessage(t1.getNameNoPrefix() + " &awas teleported to you by " + player.getNameNoPrefix());
                player.sendMessage("&aYou teleported " + t1.getNameNoPrefix() + " &ato " + t2.getNameNoPrefix());
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
            this.settingNick.add(player.getUuid());
            player.sendMessage("&6&l╔══════════════════════════════════════");
            player.sendMessage("&6&l║ &7You have started the nicname process.");
            player.sendMessage("&6&l║ &7You may cancel with /nickcancel.");
            player.sendMessage("&6&l║ &7You will be restricted until finished or until canceled.");
            player.sendMessage("&6&l║ &7Getting the UUID of the name provided.");
            UUID uuid;
            try {
                uuid = MojangUtils.getUUIDFromName(args[0]);
            } catch (Exception e) {
                player.sendMessage("&6&l║ &cThere was an error getting the UUID from the name, nickname proccess cancelled.");
                player.sendMessage("&6&l╚══════════════════════════════════════");
                this.settingNick.remove(player.getUuid());
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
                            confirmNick.put(player.getUuid(), profile);
                            player.sendMessage("&6&l║ &7The profile has been received, you need to confirm the request.");
                            player.sendMessage("&6&l║ &7To confirm type &a/nickconfirm&7. To cancel type &c/nickcancel&7.");
                            player.sendMessage("&6&l╚══════════════════════════════════════");
                            //TODO Display profile somewhere here and in the one below as well
                        }
                    }
                }.runTaskTimerAsynchronously(this, 0L, 10L);
            } else {
                player.sendMessage("&6&l║ &7There is information for the nickname you provided.");
                player.sendMessage("&6&l║ &7You must confirm the nickname information.");
                player.sendMessage("&6&l║ &7To confirm type &a/nickconfirm &7tocancel type &c/nickcancel&7.");
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
                } else {
                    player.sendMessage("&cYou are not currently setting a nickname.");
                    return true;
                }
            } else {
                sender.sendMessage("§cOnly players may use that command.");
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("vanish")) {
            sender.sendMessage("§cNot implemented yet.ª");
        } else if (cmd.getName().equalsIgnoreCase("viewprofile")) {
            if (sender instanceof Player) {
                FirecraftPlayer player = onlineFirecraftPlayers.get(((Player) sender).getUniqueId());
                if (Rank.isStaff(player.getRank())) {
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
                        for (FirecraftPlayer p : onlineFirecraftPlayers.values()) {
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
                    player.sendMessage("&7Rank: " + target.getRank().getBaseColor() + target.getRank().toString());
                    player.sendMessage("&7Channel: " + target.getChannel().getColor() + target.getChannel().toString());
                }
            }
        }

        return true;
    }

    private void gamemodeShortcut(CommandSender sender, GameMode mode, String[] args) {
        if (sender instanceof Player) {
            FirecraftPlayer player = getFirecraftPlayer(((Player) sender).getUniqueId());
            if (player.getRank().equals(Rank.JUNIOR_ADMIN) || player.getRank().isHigher(Rank.JUNIOR_ADMIN)) {
                FirecraftPlayer target = null;
                if (args.length > 0) {
                    for (FirecraftPlayer p : getFirecraftPlayers()) {
                        if (p.getName().equalsIgnoreCase(args[1])) {
                            target = p;
                        }
                    }
                }

                if (target != null) {
                    if (player.getRank().equals(Rank.JUNIOR_ADMIN)) {
                        player.sendMessage("&cOnly Admins and Higher can set other player's gamemodes.");
                        return;
                    }

                    //TODO Add a bypass for FCT Members
                    if (target.getRank().equals(player.getRank()) || target.getRank().isHigher(player.getRank())) {
                        player.sendMessage("&cYou cannot set the gamemode of someone of the same rank or higher than you.");
                        return;
                    }

                    target.setGamemode(mode);
                    player.sendMessage("&aYou have set " + target.getNameNoPrefix() + "&a's gamemode to &b" + mode.toString().toLowerCase());
                    target.sendMessage("&aYour gamemode has been set to &b" + mode.toString().toLowerCase() + " &aby " + player.getNameNoPrefix());
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
}