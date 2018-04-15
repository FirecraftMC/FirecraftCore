package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.FirecraftPlayer;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.classes.utils.CmdUtils;
import net.firecraftmc.shared.enums.Rank;
import org.bukkit.GameMode;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class GamemodeManager implements TabExecutor, Listener {
    private FirecraftCore plugin;
    
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
                if (!CmdUtils.checkArgCountGreater(sender, args, 0)) return true;
                
                FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
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
                    
                    gamemodeShortcut(sender, mode, args, false);
                } else {
                    player.sendMessage("&cOnly Trial Admins and above can use the gamemode command.");
                    return true;
                }
            } else if (sender instanceof ConsoleCommandSender) {
                sender.sendMessage("§cIt is not yet implemented for console to set gamemodes.");
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("gmc")) {
            gamemodeShortcut(sender, GameMode.CREATIVE, args, true);
        } else if (cmd.getName().equalsIgnoreCase("gms")) {
            gamemodeShortcut(sender, GameMode.SURVIVAL, args, true);
        } else if (cmd.getName().equalsIgnoreCase("gmsp")) {
            gamemodeShortcut(sender, GameMode.SPECTATOR, args, true);
        } else if (cmd.getName().equalsIgnoreCase("gma")) {
            gamemodeShortcut(sender, GameMode.ADVENTURE, args, true);
        }
        
        return true;
    }
    
    public List<String> onTabComplete(CommandSender sender, Command cmd, String s, String[] args) {
        return null;
    }
    
    private void gamemodeShortcut(CommandSender sender, GameMode mode, String[] args, boolean gmAl) {
        if (sender instanceof Player) {
            FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
            if (player.getMainRank().equals(Rank.TRIAL_ADMIN) || player.getMainRank().isHigher(Rank.TRIAL_ADMIN)) {
                FirecraftPlayer target = null;
                if (args.length > 1) {
                    target = plugin.getPlayerManager().getPlayer(args[1]);
                }
                
                if (target != null) {
                    if (player.getMainRank().equals(Rank.TRIAL_ADMIN)) {
                        player.sendMessage("&cOnly Admins and Higher can set other player's gamemodes.");
                        return;
                    }
                    
                    if (target.getMainRank().equals(player.getMainRank()) || target.getMainRank().isHigher(player.getMainRank())) {
                        if (!target.getMainRank().equals(Rank.FIRECRAFT_TEAM) && !target.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                            player.sendMessage("&cYou cannot set the gamemode of someone of the same rank or higher than you.");
                            return;
                        }
                    }
                    
                    target.setGamemode(mode);
                    player.sendMessage("&aYou have set " + target.getDisplayName() + "&a's gamemode to &b" + mode.toString().toLowerCase());
                    target.sendMessage("&aYour gamemode has been set to &b" + mode.toString().toLowerCase() + " &aby " + player.getDisplayName());
                    return;
                }
                
                player.setGamemode(mode);
                player.sendMessage("&aYou set your own gamemode to &b" + mode.toString().toLowerCase());
            } else {
                player.sendMessage("&cOnly Trial Admins and above can use the gamemode command.");
            }
        } else if (sender instanceof ConsoleCommandSender) {
            sender.sendMessage("§cIt is not yet implemented for console to set gamemodes.");
        }
    }
}