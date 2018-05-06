package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.*;
import net.firecraftmc.shared.enums.Rank;
import net.firecraftmc.shared.packets.staffchat.FPSCSetGamemode;
import net.firecraftmc.shared.packets.staffchat.FPSCSetGamemodeOthers;
import org.bukkit.GameMode;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class GamemodeManager implements TabExecutor, Listener {
    private final FirecraftCore plugin;

    private static final String prefix = "&d&l[Gamemode] ";
    
    public GamemodeManager(FirecraftCore plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent e) {
        Player player = e.getPlayer();
        GameMode mode = player.getGameMode();
        boolean flight = player.getAllowFlight();
        new BukkitRunnable() {
            public void run() {
                player.setGameMode(mode);
                player.setAllowFlight(flight);
            }
        }.runTaskLater(plugin, 5L);
    }
    
    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (cmd.getName().equalsIgnoreCase("gamemode")) {
            if (sender instanceof Player) {
                if (!Utils.Command.checkArgCountGreater(sender, args, 0)) return true;
                
                FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
                if (!Utils.checkFirecraftPlayer((Player) sender, player)) return true;
                GameMode mode = null;
                if (Utils.Command.checkCmdAliases(args, 0, "creative", "c", "1")) {
                    mode = GameMode.CREATIVE;
                } else if (Utils.Command.checkCmdAliases(args, 0, "survival", "s", "0")) {
                    mode = GameMode.SURVIVAL;
                } else if (Utils.Command.checkCmdAliases(args, 0, "adventure", "a", "2")) {
                    mode = GameMode.ADVENTURE;
                } else if (Utils.Command.checkCmdAliases(args, 0, "spectator", "sp", "spec", "3")) {
                    mode = GameMode.SPECTATOR;
                }
                
                if (mode == null) {
                    player.sendMessage(prefix + Messages.invalidGamemode);
                    return true;
                }
                
                gamemodeShortcut(sender, mode, args);
            } else if (sender instanceof ConsoleCommandSender) {
                sender.sendMessage(prefix + Messages.consoleNotImplemented);
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
        }
        
        return true;
    }
    
    public List<String> onTabComplete(CommandSender sender, Command cmd, String s, String[] args) {
        return null;
    }
    
    private void gamemodeShortcut(CommandSender sender, GameMode mode, String[] args) {
        if (sender instanceof Player) {
            FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
            if (player.getMainRank().equals(Rank.BUILD_TEAM) || player.getMainRank().isEqualToOrHigher(Rank.TRIAL_ADMIN)) {
                FirecraftPlayer target = null;
                if (args.length > 1) {
                    target = plugin.getPlayerManager().getPlayer(args[1]);
                }
                
                if (target != null) {
                    if (player.getMainRank().equals(Rank.ADMIN)) {
                        player.sendMessage(prefix + Messages.onlyAdminHigherSetOthersGamemode);
                        return;
                    }
                    
                    if (target.getMainRank().isEqualToOrHigher(player.getMainRank())) {
                        if (!(target.getMainRank().equals(Rank.FIRECRAFT_TEAM) && player.getMainRank().equals(Rank.FIRECRAFT_TEAM))) {
                            player.sendMessage(prefix + Messages.cannotSetGmOfSameRankOrHigher);
                            return;
                        }
                    }
                    
                    target.setGamemode(mode);
                    FPSCSetGamemodeOthers setGamemode = new FPSCSetGamemodeOthers(plugin.getFirecraftServer(), player.getUniqueId(), mode, target.getUniqueId());
                    plugin.getSocket().sendPacket(setGamemode);
                    return;
                }
                
                player.setGamemode(mode);
                FPSCSetGamemode setGamemode = new FPSCSetGamemode(plugin.getFirecraftServer(), player.getUniqueId(), mode);
                plugin.getSocket().sendPacket(setGamemode);
            } else {
                player.sendMessage(prefix + Messages.noPermission);
            }
        } else if (sender instanceof ConsoleCommandSender) {
            sender.sendMessage(prefix + Messages.consoleNotImplemented);
        }
    }
}