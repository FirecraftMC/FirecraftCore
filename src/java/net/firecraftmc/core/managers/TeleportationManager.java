package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.core.classes.TPRequest;
import net.firecraftmc.shared.classes.FirecraftPlayer;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.classes.utils.CmdUtils;
import net.firecraftmc.shared.enums.Rank;
import net.firecraftmc.shared.packets.staffchat.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class TeleportationManager implements TabExecutor, Listener {
    private final FirecraftCore plugin;
    
    private final Map<UUID, Location> lastLocation = new HashMap<>();
    private final TreeMap<Long, TPRequest> requests = new TreeMap<>();
    
    public TeleportationManager(FirecraftCore plugin) {
        this.plugin = plugin;
        
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
                            requester.sendMessage("&aYour teleport request to " + requested.getDisplayName() + " &ahas expired.");
                            requested.sendMessage("&aThe teleport request from " + requester.getDisplayName() + " &ahas expired.");
                        }
                    }
                });
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }
    
    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent e) {
        this.lastLocation.put(e.getPlayer().getUniqueId(), e.getFrom());
    }
    
    @EventHandler
    public void onPlayerDeath(PlayerRespawnEvent e) {
        e.setRespawnLocation(plugin.getServerSpawn());
    }
    
    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (sender instanceof ConsoleCommandSender) {
            sender.sendMessage("§cConsole is not able to teleport players.");
            return true;
        }
        
        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
        if (!Utils.checkFirecraftPlayer((Player) sender, player)) return true;
        
        if (cmd.getName().equalsIgnoreCase("teleport")) {
            if (!player.getMainRank().isEqualToOrHigher(Rank.MOD)) {
                //TODO Add checks for staff based ranks for SrMods and below
                player.sendMessage("&cMods and above can teleport directly.");
                return true;
            }
            
            if (args.length == 1) {
                FirecraftPlayer target = plugin.getPlayerManager().getPlayer(args[0]);
                
                if (target == null) {
                    player.sendMessage("&cCould not find the player " + args[0]);
                    return true;
                }
                
                if (target.getMainRank().isHigher(player.getMainRank())) {
                    if (target.isVanished()) {
                        player.sendMessage("&cCould not find the player " + args[0]);
                        return true;
                    }
                }
                
                player.teleport(target.getLocation());
                FPSCTeleport teleport = new FPSCTeleport(plugin.getFirecraftServer(), player, target);
                plugin.getSocket().sendPacket(teleport);
                if (target.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                    target.sendMessage(player.getName() + " &ateleported to you.");
                    player.sendMessage("&7&oYou teleported to a Firecraft Team member, they were notified of that action.");
                }
            } else if (args.length == 2) {
                if (player.getMainRank().equals(Rank.MOD)) {
                    player.sendMessage("&cOnly Trial Admins and above can teleport players to other players.");
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
                    player.sendMessage("&cThe name provided for the first player is invalid.");
                    return true;
                }
                
                if (t2 == null) {
                    player.sendMessage("&cThe name provided for the second player is invalid.");
                    return true;
                }
                
                if (t1.getMainRank().isHigher(player.getMainRank())) {
                    if (t1.isVanished()) {
                        player.sendMessage("&cThe name provided for the first player is invalid.");
                        return true;
                    }
                }
    
                if (t2.getMainRank().isHigher(player.getMainRank())) {
                    if (t2.isVanished()) {
                        player.sendMessage("&cThe name provided for the second player is invalid.");
                        return true;
                    }
                }
                
                if (t1.getMainRank().isEqualToOrHigher(player.getMainRank())) {
                    player.sendMessage("&cYou cannot forcefully teleport players equal to or higher than your rank.");
                    return true;
                }
                
                t1.teleport(t2.getLocation());
                FPSCTeleportOthers teleport = new FPSCTeleportOthers(plugin.getFirecraftServer(), player, t1, t2);
                plugin.getSocket().sendPacket(teleport);
            } else {
                player.sendMessage("&cYou did not provide the correct number of arguments.");
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("back")) {
            if (this.lastLocation.containsKey(player.getUniqueId())) {
                player.teleport(this.lastLocation.get(player.getUniqueId()));
                player.sendMessage("§aTeleported you to your last location.");
            } else {
                player.sendMessage("§cYou currently do not have a last location.");
            }
        } else if (cmd.getName().equalsIgnoreCase("tphere")) {
            if (!CmdUtils.checkArgCountExact(sender, args, 1)) return true;
            
            if (!(player.getMainRank().equals(Rank.TRIAL_ADMIN) || player.getMainRank().isHigher(Rank.TRIAL_ADMIN))) {
                player.sendMessage("&cOnly Trial Admins or above can teleport players to themselves.");
                return true;
            }
            
            FirecraftPlayer target = null;
            for (FirecraftPlayer p : plugin.getPlayerManager().getPlayers()) {
                if (p.getName().equalsIgnoreCase(args[0])) {
                    target = p;
                }
            }
            
            if (target == null) {
                player.sendMessage("&cThe name you provided is not valid.");
                return true;
            }
    
            if (target.getMainRank().isHigher(player.getMainRank())) {
                if (target.isVanished()) {
                    player.sendMessage("&cCould not find the player " + args[0]);
                    return true;
                }
            }
            
            target.teleport(player.getLocation());
            FPSCTeleportHere tpHere = new FPSCTeleportHere(plugin.getFirecraftServer(), player, target);
            plugin.getSocket().sendPacket(tpHere);
        } else if (cmd.getName().equalsIgnoreCase("tpall")) {
            if (player.getMainRank().equals(Rank.HEAD_ADMIN) || player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                if (player.getMainRank().equals(Rank.HEAD_ADMIN)) {
                    for (FirecraftPlayer p : plugin.getPlayerManager().getPlayers()) {
                        if (!p.getUniqueId().equals(player.getUniqueId())) {
                            if (p.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                                p.sendMessage(player.getDisplayName() + " &aissued a tpall but you were not teleported because of your rank.");
                            } else {
                                p.teleport(player.getLocation());
                                p.sendMessage(player.getDisplayName() + " &aissued a tpall so you were teleported to them.");
                            }
                        }
                    }
                    player.sendMessage("&aYou teleported all players except Firecraft Team members to you.");
                } else {
                    for (FirecraftPlayer p : plugin.getPlayerManager().getPlayers()) {
                       if (!p.getUniqueId().equals(player.getUniqueId())) {
                           p.teleport(player.getLocation());
                           p.sendMessage(player.getDisplayName() + " &aissued a tpall so you were teleported to them.");
                       }
                    }
                    player.sendMessage("&aYou teleported all players to you.");
                }
            } else {
                player.sendMessage("&cOnly Head Admins or Firecraft Team members can use tpall.");
            }
        } else if (cmd.getName().equalsIgnoreCase("tpa")) {
            if (!CmdUtils.checkArgCountExact(sender, args, 1)) return true;
            FirecraftPlayer target = plugin.getPlayerManager().getPlayer(args[0]);
            if (target == null) {
                player.sendMessage("&cCould not find a player by that name.");
                return true;
            }
    
            if (target.getMainRank().isHigher(player.getMainRank())) {
                if (target.isVanished()) {
                    player.sendMessage("&cCould not find a player by that name.");
                    return true;
                }
            }
            
            long currentTime = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
            long expire = currentTime + 60;
            
            this.requests.put(currentTime, new TPRequest(player.getUniqueId(), target.getUniqueId(), expire));
            player.sendMessage("&aSent a teleport request to &b" + target.getDisplayName() + "&a.");
            target.sendMessage(player.getDisplayName() + " &ahas sent a teleport request to you.");
            target.sendMessage("&aType /tpaccept to accept or /tpdeny to deny the request.");
            target.sendMessage("&aThe request will expire in 60 seconds.");
        } else if (cmd.getName().equalsIgnoreCase("tpaccept")) {
            Map.Entry<Long, TPRequest> entry = getRequestByRequested(player.getUniqueId());
            if (entry == null) {
                player.sendMessage("&cCould not find a request, did it expire?");
                return true;
            }
            TPRequest request = entry.getValue();
            Player r = Bukkit.getPlayer(request.getRequester());
            if (r == null) {
                player.sendMessage("&aThe one you requested to teleport to you is no longer online.");
                return true;
            }
            
            FirecraftPlayer requester = plugin.getPlayerManager().getPlayer(r.getUniqueId());
            requester.sendMessage(player.getDisplayName() + " &ahas accepted your teleport request.");
            player.sendMessage("&aYou accepted " + requester.getDisplayName() + "&a's teleport request.");
            requester.teleport(player.getLocation());
            this.requests.remove(entry.getKey());
        } else if (cmd.getName().equalsIgnoreCase("tpdeny")) {
            Map.Entry<Long, TPRequest> entry = getRequestByRequested(player.getUniqueId());
            if (entry == null) {
                player.sendMessage("&cCould not find a request, did it expire?");
                return true;
            }
            TPRequest request = entry.getValue();
            Player r = Bukkit.getPlayer(request.getRequester());
            if (r == null) {
                player.sendMessage("&aThe one you requested to teleport to you is no longer online.");
                return true;
            }
    
            FirecraftPlayer requester = plugin.getPlayerManager().getPlayer(r.getUniqueId());
            requester.sendMessage(player.getDisplayName() + " &ahas denied your teleport request.");
            player.sendMessage("&aYou denied " + requester.getDisplayName() + "&a's teleport request.");
            this.requests.remove(entry.getKey());
        } else if (cmd.getName().equalsIgnoreCase("setspawn")) {
            if (player.getMainRank().isEqualToOrHigher(Rank.HEAD_ADMIN)) {
                plugin.setServerSpawn(player.getLocation());
                player.sendMessage("&aYou set the server spawn to your location.");
                return true;
            } else {
                player.sendMessage("&cYou cannot set the spawnpoint.");
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("spawn")) {
            player.teleport(plugin.getServerSpawn());
            player.sendMessage("&aSent you to the server spawnpoint.");
        }
        
        return true;
    }
    
    public List<String> onTabComplete(CommandSender sender, Command cmd, String s, String[] args) {
        return null;
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