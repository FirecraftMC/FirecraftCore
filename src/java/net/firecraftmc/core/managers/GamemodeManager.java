package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.Prefixes;
import net.firecraftmc.shared.classes.model.player.FirecraftPlayer;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.classes.enums.Rank;
import net.firecraftmc.shared.packets.staffchat.FPSCSetGamemode;
import net.firecraftmc.shared.packets.staffchat.FPSCSetGamemodeOthers;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class GamemodeManager implements CommandExecutor, Listener {
    private final FirecraftCore plugin;

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
                if (player.isRecording()) {
                    player.sendMessage(Prefixes.GAMEMODE + Messages.recordingNoUse);
                    return true;
                }
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
                    player.sendMessage(Prefixes.GAMEMODE + Messages.invalidGamemode);
                    return true;
                }

                gamemodeShortcut(sender, mode, args);
            } else if (sender instanceof ConsoleCommandSender) {
                sender.sendMessage(Prefixes.GAMEMODE + Messages.consoleNotImplemented);
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

    /**
     * Just a little utility method to prevent repeat code
     *
     * @param sender The CommandSender from the command
     * @param mode   The target Gamemode
     * @param args   The command arguments
     */
    private void gamemodeShortcut(CommandSender sender, GameMode mode, String[] args) {
        if (sender instanceof Player) {
            FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
            if (player.isRecording()) {
                player.sendMessage(Prefixes.GAMEMODE + Messages.recordingNoUse);
                return;
            }
            FirecraftPlayer target = null;
            if (args.length > 0) {
                target = plugin.getPlayerManager().getPlayer(args[0]);
            }

            if (args.length > 1) {
                target = plugin.getPlayerManager().getPlayer(args[1]);
            }

            if (target != null) {
                if (!target.getName().equalsIgnoreCase("creative") || !target.getName().equalsIgnoreCase("survival")) {
                    if (!player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                        player.sendMessage(Prefixes.GAMEMODE + Messages.onlyAdminHigherSetOthersGamemode);
                        return;
                    }

                    if (target.getMainRank().isEqualToOrHigher(player.getMainRank())) {
                        if (!(target.getMainRank().equals(Rank.FIRECRAFT_TEAM) && player.getMainRank().equals(Rank.FIRECRAFT_TEAM))) {
                            player.sendMessage(Prefixes.GAMEMODE + Messages.cannotSetGmOfSameRankOrHigher);
                            return;
                        }
                    }

                    target.setGamemode(mode);
                    FPSCSetGamemodeOthers setGamemode = new FPSCSetGamemodeOthers(plugin.getFirecraftServer(), player.getUniqueId(), mode, target.getUniqueId());
                    plugin.getSocket().sendPacket(setGamemode);
                    return;
                }
            }

            if (mode.equals(GameMode.CREATIVE)) {
                if (!(player.getMainRank().equals(Rank.BUILD_TEAM) || player.getMainRank().isEqualToOrHigher(Rank.ADMIN))) {
                    player.sendMessage(Prefixes.GAMEMODE + Messages.noPermission);
                    return;
                }
            }

            if (mode.equals(GameMode.SPECTATOR)) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.MODERATOR)) {
                    player.sendMessage(Prefixes.GAMEMODE + Messages.noPermission);
                    return;
                }
            }

            if (mode.equals(GameMode.ADVENTURE)) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                    player.sendMessage(Prefixes.GAMEMODE + Messages.noPermission);
                    return;
                }
            }

            if (player.getPlayer().getGameMode().equals(mode)) {
                player.sendMessage(Prefixes.GAMEMODE + Messages.alreadyInGamemode);
                return;
            }

            player.setGamemode(mode);
            FPSCSetGamemode setGamemode = new FPSCSetGamemode(plugin.getFirecraftServer(), player.getUniqueId(), mode);
            plugin.getSocket().sendPacket(setGamemode);
        } else if (sender instanceof ConsoleCommandSender) {
            sender.sendMessage(Prefixes.GAMEMODE + Messages.consoleNotImplemented);
        }
    }
}