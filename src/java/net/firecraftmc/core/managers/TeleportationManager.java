package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.model.server.FirecraftServer;
import net.firecraftmc.shared.classes.model.player.FirecraftPlayer;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.classes.enums.Rank;
import net.firecraftmc.shared.classes.model.player.TPRequest;
import net.firecraftmc.shared.packets.staffchat.FPSCTeleport;
import net.firecraftmc.shared.packets.staffchat.FPSCTeleportHere;
import net.firecraftmc.shared.packets.staffchat.FPSCTeleportOthers;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class TeleportationManager implements CommandExecutor, Listener {
    private final FirecraftCore plugin;
    
    private final Map<UUID, Location> lastLocation = new HashMap<>();
    private final TreeMap<Long, TPRequest> requests = new TreeMap<>();
    
    public TeleportationManager(FirecraftCore plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        new BukkitRunnable() {
            public void run() {
                Iterator<Long> iter = requests.keySet().iterator();
                iter.forEachRemaining((value) ->  {
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
    }
    
    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent e) {
        this.lastLocation.put(e.getPlayer().getUniqueId(), e.getFrom());
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerRespawnEvent e) {
        e.setRespawnLocation(plugin.getServerSpawn());
    }
    
    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (sender instanceof ConsoleCommandSender) {
            sender.sendMessage(Messages.onlyPlayers);
            return true;
        }
        
        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
        if (!Utils.checkFirecraftPlayer((Player) sender, player)) return true;
        
        if (cmd.getName().equalsIgnoreCase("teleport")) {
            if (!player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                if (!plugin.getStaffmodeManager().inStaffMode(player)) {
                    player.sendMessage(Messages.noPermission);
                    return true;
                }
            }

            if (player.isRecording()) {
                player.sendMessage(Messages.recordingNoUse);
                return true;
            }
            
            if (args.length == 1) {
                FirecraftPlayer target = plugin.getPlayerManager().getPlayer(args[0]);
                if (target == null) {
                    player.sendMessage(Messages.couldNotFindPlayer(args[0]));
                    return true;
                }
                
                if (target.getMainRank().isHigher(player.getMainRank())) {
                    if (target.isVanished()) {
                        player.sendMessage(Messages.couldNotFindPlayer(args[0]));
                        return true;
                    }
                }
                
                player.teleport(target.getLocation());
                FPSCTeleport teleport = new FPSCTeleport(plugin.getFCServer().getId(), player.getUniqueId(), target.getUniqueId());
                plugin.getSocket().sendPacket(teleport);
            } else if (args.length == 2) {
                if (player.getMainRank().equals(Rank.MODERATOR)) {
                    player.sendMessage(Messages.noPermission);
                    return true;
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
                    return true;
                }
                
                if (t2 == null) {
                    player.sendMessage(Messages.tpTargetInvalid("second"));
                    return true;
                }
                
                if (t1.getMainRank().isHigher(player.getMainRank())) {
                    if (t1.isVanished()) {
                        player.sendMessage(Messages.tpTargetInvalid("first"));
                        return true;
                    }
                }
    
                if (t2.getMainRank().isHigher(player.getMainRank())) {
                    if (t2.isVanished()) {
                        player.sendMessage(Messages.tpTargetInvalid("second"));
                        return true;
                    }
                }
                
                if (t1.getMainRank().isEqualToOrHigher(player.getMainRank())) {
                    player.sendMessage(Messages.noPermToTpHigherRank);
                    return true;
                }
                
                t1.teleport(t2.getLocation());
                FPSCTeleportOthers teleport = new FPSCTeleportOthers(plugin.getFCServer().getId(), player.getUniqueId(), t1.getUniqueId(), t2.getUniqueId());
                plugin.getSocket().sendPacket(teleport);
            } else {
                player.sendMessage(Messages.notEnoughArgs);
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("back")) {
            if (this.lastLocation.containsKey(player.getUniqueId())) {
                player.teleport(this.lastLocation.get(player.getUniqueId()));
                player.sendMessage(Messages.back);
            } else {
                player.sendMessage(Messages.noBackLocation);
            }
        } else if (cmd.getName().equalsIgnoreCase("tphere")) {
            if (!Utils.Command.checkArgCountExact(sender, args, 1)) return true;
            
            if (!player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                System.out.println("Player is not " + Rank.MODERATOR.toString());
                if (plugin.getStaffmodeManager().inStaffMode(player)) {
                    System.out.println("Player is in staff mode.");
                    if (!player.getMainRank().isEqualToOrHigher(Rank.MODERATOR)) {
                        System.out.println("But they are not a moderator.");
                        player.sendMessage(Messages.noPermission);
                        return true;
                    }
                } else {
                    player.sendMessage(Messages.noPermission);
                    return true;
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
                return true;
            }
    
            if (target.getMainRank().isHigher(player.getMainRank())) {
                if (target.isVanished()) {
                    player.sendMessage(Messages.couldNotFindPlayer(args[0]));
                    return true;
                }
            }
            
            target.teleport(player.getLocation());
            FPSCTeleportHere tpHere = new FPSCTeleportHere(plugin.getFCServer().getId(), player.getUniqueId(), target.getUniqueId());
            plugin.getSocket().sendPacket(tpHere);
        } else if (cmd.getName().equalsIgnoreCase("tpall")) {
            if (player.getMainRank().equals(Rank.HEAD_ADMIN) || player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                if (player.isRecording()) {
                    player.sendMessage(Messages.recordingNoUse);
                    return true;
                }
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
            } else {
                player.sendMessage(Messages.noPermission);
            }
        } else if (cmd.getName().equalsIgnoreCase("tpa")) {
            if (!Utils.Command.checkArgCountExact(sender, args, 1)) return true;
            FirecraftPlayer target = plugin.getPlayerManager().getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(Messages.couldNotFindPlayer(args[0]));
                return true;
            }
    
            if (target.getMainRank().isHigher(player.getMainRank())) {
                if (target.isVanished()) {
                    player.sendMessage(Messages.couldNotFindPlayer(args[0]));
                    return true;
                }
            }
            if (target.isIgnoring(player.getUniqueId())) {
                player.sendMessage("&cYou are not allowed to request to teleport to " + target.getName() + " because they are ignoring you.");
                return true;
            }
            
            long currentTime = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
            long expire = currentTime + 60;

            this.requests.put(currentTime, new TPRequest(player.getUniqueId(), target.getUniqueId(), expire));
            target.sendMessage(Messages.tpRequestReceive(player.getName()));
            player.sendMessage(Messages.tpRequestSend(target.getName()));
        } else if (cmd.getName().equalsIgnoreCase("tpaccept")) {
            Map.Entry<Long, TPRequest> entry = getRequestByRequested(player.getUniqueId());
            if (entry == null) {
                player.sendMessage(Messages.couldNotFindRequest);
                return true;
            }
            TPRequest request = entry.getValue();
            Player r = Bukkit.getPlayer(request.getRequester());
            if (r == null) {
                player.sendMessage(Messages.requesterOffline);
                return true;
            }
            
            FirecraftPlayer requester = plugin.getPlayerManager().getPlayer(r.getUniqueId());
            requester.sendMessage(Messages.requestRespondSender("accepted", player.getName()));
            player.sendMessage(Messages.requestRespondReceiver("accepted", requester.getName()));
            requester.teleport(player.getLocation());
            this.requests.remove(entry.getKey());
        } else if (cmd.getName().equalsIgnoreCase("tpdeny")) {
            Map.Entry<Long, TPRequest> entry = getRequestByRequested(player.getUniqueId());
            if (entry == null) {
                player.sendMessage(Messages.couldNotFindRequest);
                return true;
            }
            TPRequest request = entry.getValue();
            Player r = Bukkit.getPlayer(request.getRequester());
            if (r == null) {
                player.sendMessage(Messages.requesterOffline);
                return true;
            }
    
            FirecraftPlayer requester = plugin.getPlayerManager().getPlayer(r.getUniqueId());
            requester.sendMessage(Messages.requestRespondSender("denied", player.getName()));
            player.sendMessage(Messages.requestRespondReceiver("denied", requester.getName()));
            this.requests.remove(entry.getKey());
        } else if (cmd.getName().equalsIgnoreCase("setspawn")) {
            if (player.getMainRank().isEqualToOrHigher(Rank.HEAD_ADMIN)) {
                if (player.isRecording()) {
                    player.sendMessage(Messages.recordingNoUse);
                    return true;
                }
                plugin.setServerSpawn(player.getLocation());
                player.sendMessage(Messages.setSpawn);
                return true;
            } else {
                player.sendMessage(Messages.noPermission);
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("spawn")) {
            player.teleport(plugin.getServerSpawn());
            player.sendMessage(Messages.sendToSpawn);
        }
        
        return true;
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