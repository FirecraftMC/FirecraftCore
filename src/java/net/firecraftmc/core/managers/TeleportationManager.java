package net.firecraftmc.core.managers;

import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.model.player.TPRequest;
import net.firecraftmc.api.model.server.FirecraftServer;
import net.firecraftmc.api.packets.staffchat.*;
import net.firecraftmc.api.toggles.Toggle;
import net.firecraftmc.api.util.Messages;
import net.firecraftmc.api.util.Utils;
import net.firecraftmc.core.FirecraftCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class TeleportationManager implements Listener {
    private final FirecraftCore plugin;
    
    private final Map<UUID, Location> lastLocation = new HashMap<>();
    private final TreeMap<Long, TPRequest> requests = new TreeMap<>();
    
    public TeleportationManager(FirecraftCore plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        new BukkitRunnable() {
            public void run() {
                Iterator<Long> iter = requests.keySet().iterator();
                iter.forEachRemaining((value) -> {
                    long expire = requests.get(value).getExpire();
                    if (expire <= TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())) {
                        FirecraftPlayer requester = plugin.getPlayerManager().getPlayer(requests.get(value).getRequester());
                        FirecraftPlayer requested = plugin.getPlayerManager().getPlayer(requests.get(value).getRequested());
                        
                        if ((requester == null) || (requested == null)) {
                            iter.remove();
                            plugin.getLogger().log(Level.INFO, "Removed a request with a null requester or requested");
                        } else {
                            requester.sendMessage(Messages.tpRequestExpire_Requester(requested.getDisplayName()));
                            requested.sendMessage(Messages.tpRequestExpire_Target(requester.getDisplayName()));
                        }
                    }
                    iter.remove();
                });
            }
        }.runTaskTimer(plugin, 0L, 20L);
        
        plugin.getSocket().addSocketListener(packet -> {
            FirecraftServer server = plugin.getServerManager().getServer(packet.getServerId());
            if (packet instanceof FPSCTeleport) {
                FPSCTeleport teleport = (FPSCTeleport) packet;
                FirecraftPlayer staffMember = plugin.getPlayerManager().getPlayer(teleport.getPlayer());
                FirecraftPlayer target = plugin.getPlayerManager().getPlayer(teleport.getTarget());
                String format = Utils.Chat.formatTeleport(server, staffMember, target);
                Utils.Chat.sendStaffChatMessage(plugin.getPlayerManager().getPlayers(), staffMember, format);
            } else if (packet instanceof FPSCTeleportOthers) {
                FPSCTeleportOthers teleportOthers = (FPSCTeleportOthers) packet;
                FirecraftPlayer staffMember = plugin.getPlayerManager().getPlayer(teleportOthers.getPlayer());
                FirecraftPlayer target1 = plugin.getPlayerManager().getPlayer(teleportOthers.getTarget1());
                FirecraftPlayer target2 = plugin.getPlayerManager().getPlayer(teleportOthers.getTarget2());
                String format = Utils.Chat.formatTeleportOthers(server, staffMember, target1, target2);
                Utils.Chat.sendStaffChatMessage(plugin.getPlayerManager().getPlayers(), staffMember, format);
            } else if (packet instanceof FPSCTeleportHere) {
                FPSCTeleportHere tpHere = (FPSCTeleportHere) packet;
                FirecraftPlayer staffMember = plugin.getPlayerManager().getPlayer(tpHere.getPlayer());
                FirecraftPlayer target = plugin.getPlayerManager().getPlayer(tpHere.getTarget());
                String format = Utils.Chat.formatTeleportHere(server, staffMember, target);
                Utils.Chat.sendStaffChatMessage(plugin.getPlayerManager().getPlayers(), staffMember, format);
            }
        });
        
        FirecraftCommand teleport = new FirecraftCommand("teleport", "Teleport to another player.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                    if (!plugin.getStaffmodeManager().inStaffMode(player)) {
                        player.sendMessage(Messages.noPermission);
                        return;
                    }
                }
                
                if (args.length == 1) {
                    FirecraftPlayer target = plugin.getPlayerManager().getPlayer(args[0]);
                    if (target == null) {
                        player.sendMessage(Messages.couldNotFindPlayer(args[0]));
                        return;
                    }
                    
                    if (target.getMainRank().isHigher(player.getMainRank())) {
                        if (target.isVanished()) {
                            player.sendMessage(Messages.couldNotFindPlayer(args[0]));
                            return;
                        }
                    }
                    
                    player.teleport(target.getLocation());
                    FPSCTeleport teleport = new FPSCTeleport(plugin.getFCServer().getId(), player.getUniqueId(), target.getUniqueId());
                    plugin.getSocket().sendPacket(teleport);
                } else if (args.length == 2) {
                    if (!player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                        player.sendMessage(Messages.noPermission);
                        return;
                    }
                    
                    FirecraftPlayer t1 = null, t2 = null;
                    
                    for (FirecraftPlayer fp : plugin.getPlayerManager().getPlayers()) {
                        if (fp.getName().equalsIgnoreCase(args[0])) {
                            t1 = fp;
                        } else if (fp.getName().equalsIgnoreCase(args[1])) {
                            t2 = fp;
                        }
                    }
                    
                    if (t1 == null) {
                        player.sendMessage(Messages.tpTargetInvalid("first"));
                        return;
                    }
                    
                    if (t2 == null) {
                        player.sendMessage(Messages.tpTargetInvalid("second"));
                        return;
                    }
                    
                    if (t1.getMainRank().isHigher(player.getMainRank())) {
                        if (t1.isVanished()) {
                            player.sendMessage(Messages.tpTargetInvalid("first"));
                            return;
                        }
                    }
                    
                    if (t2.getMainRank().isHigher(player.getMainRank())) {
                        if (t2.isVanished()) {
                            player.sendMessage(Messages.tpTargetInvalid("second"));
                            return;
                        }
                    }
                    
                    if (t1.getMainRank().isHigher(player.getMainRank())) {
                        player.sendMessage(Messages.noPermToTpHigherRank);
                        return;
                    }
                    
                    t1.teleport(t2.getLocation());
                    FPSCTeleportOthers teleport = new FPSCTeleportOthers(plugin.getFCServer().getId(), player.getUniqueId(), t1.getUniqueId(), t2.getUniqueId());
                    plugin.getSocket().sendPacket(teleport);
                } else {
                    player.sendMessage(Messages.notEnoughArgs);
                }
            }
        }.setBaseRank(Rank.TRIAL_MOD).addAlias("tp");
        
        FirecraftCommand back = new FirecraftCommand("back", "Go back to your previous location") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (lastLocation.containsKey(player.getUniqueId())) {
                    player.teleport(lastLocation.get(player.getUniqueId()));
                    player.sendMessage(Messages.back);
                } else {
                    player.sendMessage(Messages.noBackLocation);
                }
            }
        }.setBaseRank(Rank.DEFAULT);
        
        FirecraftCommand tphere = new FirecraftCommand("tphere", "Teleport to another player.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (!(args.length == 1)) {
                    player.sendMessage("<ec>You must provide someone to teleport to you.");
                    return;
                }
                
                if (!player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                    if (plugin.getStaffmodeManager().inStaffMode(player)) {
                        if (!player.getMainRank().isEqualToOrHigher(Rank.MOD)) {
                            player.sendMessage(Messages.noPermission);
                            return;
                        }
                    } else {
                        player.sendMessage(Messages.noPermission);
                        return;
                    }
                }
                
                FirecraftPlayer target = null;
                for (FirecraftPlayer p : plugin.getPlayerManager().getPlayers()) {
                    if (p.getName().equalsIgnoreCase(args[0])) {
                        target = p;
                    }
                }
                
                if (target == null) {
                    player.sendMessage(Messages.couldNotFindPlayer(args[0]));
                    return;
                }
                
                if (target.getMainRank().isHigher(player.getMainRank())) {
                    if (target.isVanished()) {
                        player.sendMessage(Messages.couldNotFindPlayer(args[0]));
                        return;
                    } else {
                        player.sendMessage("<ec>You cannot teleport someone of higher rank to you.");
                        return;
                    }
                }
                
                target.teleport(player.getLocation());
                FPSCTeleportHere tpHere = new FPSCTeleportHere(plugin.getFCServer().getId(), player.getUniqueId(), target.getUniqueId());
                plugin.getSocket().sendPacket(tpHere);
            }
        }.setBaseRank(Rank.MOD);
        
        FirecraftCommand tpall = new FirecraftCommand("tpall", "Teleport all players to you") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (player.getMainRank().equals(Rank.HEAD_ADMIN)) {
                    for (FirecraftPlayer p : plugin.getPlayerManager().getPlayers()) {
                        if (!p.getUniqueId().equals(player.getUniqueId())) {
                            if (p.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                                p.sendMessage(Messages.tpAllNotTeleported(player.getDisplayName()));
                            } else {
                                p.teleport(player.getLocation());
                                p.sendMessage(Messages.tpAllTeleported(player.getDisplayName()));
                            }
                        }
                    }
                    player.sendMessage(Messages.tpAllNoFCT);
                } else {
                    for (FirecraftPlayer p : plugin.getPlayerManager().getPlayers()) {
                        if (!p.getUniqueId().equals(player.getUniqueId())) {
                            p.teleport(player.getLocation());
                            p.sendMessage(Messages.tpAllTeleported(player.getDisplayName()));
                        }
                    }
                    player.sendMessage(Messages.tpAll);
                }
            }
        }.setBaseRank(Rank.HEAD_ADMIN);
        
        FirecraftCommand tpa = new FirecraftCommand("tpa", "Request to teleport to another player") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (!(args.length == 1)) {
                    player.sendMessage("<ec>You must provide someone to request to teleport to.");
                    return;
                }
                
                FirecraftPlayer target = plugin.getPlayerManager().getPlayer(args[0]);
                if (target == null) {
                    player.sendMessage(Messages.couldNotFindPlayer(args[0]));
                    return;
                }
                
                if (target.getMainRank().isHigher(player.getMainRank())) {
                    if (target.isVanished()) {
                        player.sendMessage(Messages.couldNotFindPlayer(args[0]));
                        return;
                    }
                }
                
                if (target.isIgnoring(player.getUniqueId())) {
                    player.sendMessage("<ec>You are not allowed to request to teleport to " + target.getName() + " because they are ignoring you.");
                    return;
                }
                
                if (!target.getProfile().getToggleValue(Toggle.getToggle("teleport requests"))) {
                    if (!Rank.isStaff(player.getMainRank())) {
                        player.sendMessage("<ec>That player has teleport requests disabled");
                        return;
                    } else {
                        if (!player.getMainRank().isEqualToOrHigher(target.getMainRank())) {
                            player.sendMessage("<ec>That player has teleport requests disabled");
                            return;
                        }
                    }
                }
                
                long currentTime = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
                long expire = currentTime + 60;
                
                requests.put(currentTime, new TPRequest(player.getUniqueId(), target.getUniqueId(), expire));
                target.sendMessage(Messages.tpRequestReceive(player.getName()));
                player.sendMessage(Messages.tpRequestSend(target.getName()));
            }
        }.setBaseRank(Rank.DEFAULT);
        
        FirecraftCommand tpaccept = new FirecraftCommand("tpaccept", "Accept a teleport request") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                Map.Entry<Long, TPRequest> entry = getRequestByRequested(player.getUniqueId());
                if (entry == null) {
                    player.sendMessage(Messages.couldNotFindRequest);
                    return;
                }
                TPRequest request = entry.getValue();
                Player r = Bukkit.getPlayer(request.getRequester());
                if (r == null) {
                    player.sendMessage(Messages.requesterOffline);
                    return;
                }
                
                FirecraftPlayer requester = plugin.getPlayerManager().getPlayer(r.getUniqueId());
                requester.sendMessage(Messages.requestRespondSender("accepted", player.getName()));
                player.sendMessage(Messages.requestRespondReceiver("accepted", requester.getName()));
                requester.teleport(player.getLocation());
                requests.remove(entry.getKey());
            }
        }.setBaseRank(Rank.DEFAULT);
        
        FirecraftCommand tpdeny = new FirecraftCommand("tpdeny", "Deny a teleport request") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                Map.Entry<Long, TPRequest> entry = getRequestByRequested(player.getUniqueId());
                if (entry == null) {
                    player.sendMessage(Messages.couldNotFindRequest);
                    return;
                }
                TPRequest request = entry.getValue();
                Player r = Bukkit.getPlayer(request.getRequester());
                if (r == null) {
                    player.sendMessage(Messages.requesterOffline);
                    return;
                }
                
                FirecraftPlayer requester = plugin.getPlayerManager().getPlayer(r.getUniqueId());
                requester.sendMessage(Messages.requestRespondSender("denied", player.getName()));
                player.sendMessage(Messages.requestRespondReceiver("denied", requester.getName()));
                requests.remove(entry.getKey());
            }
        }.setBaseRank(Rank.DEFAULT);
        
        FirecraftCommand setSpawn = new FirecraftCommand("setspawn", "Sets the spawnpoint of the server to your location.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                plugin.setSpawn(player.getLocation());
                player.sendMessage("<nc>You have set the server spawnpoint to your current location.");
            }
        }.setBaseRank(Rank.HEAD_ADMIN);
        
        FirecraftCommand spawn = new FirecraftCommand("spawn", "Go to the server spawnpoint") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                player.teleport(plugin.getSpawn());
                player.sendMessage("<nc>You have been teleported to the server spawnpoint.");
            }
        }.setBaseRank(Rank.DEFAULT);
        
        FirecraftCommand randomTp = new FirecraftCommand("randomtp", "Teleport to a random person online.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                    if (!plugin.getStaffmodeManager().inStaffMode(player)) {
                        player.sendMessage(Messages.noPermission);
                        return;
                    }
                }
                
                List<FirecraftPlayer> possibleTargets = new LinkedList<>();
                for (FirecraftPlayer p : plugin.getPlayers()) {
                    if (!p.getMainRank().isEqualToOrHigher(player.getMainRank())) {
                        possibleTargets.add(p);
                    }
                }
                
                if (possibleTargets.isEmpty()) {
                    player.sendMessage("<ec>There is nobody to teleport to.");
                } else {
                    Collections.shuffle(possibleTargets);
                    FirecraftPlayer target = possibleTargets.get(new Random().nextInt(possibleTargets.size()));
                    player.teleport(target.getLocation());
                    FPSCTeleport tpPacket = new FPSCTeleport(plugin.getFCServer().getId(), player.getUniqueId(), target.getUniqueId());
                    plugin.getSocket().sendPacket(tpPacket);
                }
            }
        }.setBaseRank(Rank.TRIAL_MOD);
        
        plugin.getCommandManager().addCommands(teleport, back, tphere, tpall, tpa, tpaccept, tpdeny, setSpawn, spawn, randomTp);
    }
    
    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent e) {
        this.lastLocation.put(e.getPlayer().getUniqueId(), e.getFrom());
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerRespawnEvent e) {
        e.setRespawnLocation(plugin.getSpawn());
    }
    
    private Map.Entry<Long, TPRequest> getRequestByRequested(UUID id) {
        for (Map.Entry<Long, TPRequest> entry : requests.entrySet()) {
            if (entry.getValue().getRequested().equals(id)) {
                return entry;
            }
        }
        return null;
    }
}