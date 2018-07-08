package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.*;
import net.firecraftmc.shared.classes.enums.Rank;
import net.firecraftmc.shared.classes.model.player.FirecraftPlayer;
import net.firecraftmc.shared.classes.model.server.FirecraftServer;
import net.firecraftmc.shared.packets.staffchat.*;
import org.bukkit.GameMode;
import org.bukkit.command.*;
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

        plugin.getSocket().addSocketListener(packet -> {
            FirecraftServer server = plugin.getServerManager().getServer(packet.getServerId());
            if (packet instanceof FPacketStaffChat) {
                FirecraftPlayer staffMember = plugin.getPlayerManager().getPlayer(((FPacketStaffChat) packet).getPlayer());
                if (packet instanceof FPSCSetGamemode) {
                    FPSCSetGamemode setGamemode = (FPSCSetGamemode) packet;
                    String format = Utils.Chat.formatSetGamemode(server, staffMember, setGamemode.getMode());
                    Utils.Chat.sendStaffChatMessage(plugin.getPlayerManager().getPlayers(), staffMember, format);
                } else if (packet instanceof FPSCSetGamemodeOthers) {
                    FPSCSetGamemodeOthers setGamemodeOthers = (FPSCSetGamemodeOthers) packet;
                    FirecraftPlayer target = plugin.getPlayerManager().getPlayer(setGamemodeOthers.getTarget());
                    String format = Utils.Chat.formatSetGamemodeOthers(server, staffMember, setGamemodeOthers.getMode(), target);
                    Utils.Chat.sendStaffChatMessage(plugin.getPlayerManager().getPlayers(), staffMember, format);
                }
            }
        });
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

                    target.setGameMode(mode);
                    if (plugin.getFCServer() == null) {
                        return;
                    }
                    FPSCSetGamemodeOthers setGamemode = new FPSCSetGamemodeOthers(plugin.getFCServer().getId(), player.getUniqueId(), mode, target.getUniqueId());
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

            player.setGameMode(mode);
            if (plugin.getFCServer() == null) {
                return;
            }
            FPSCSetGamemode setGamemode = new FPSCSetGamemode(plugin.getFCServer().getId(), player.getUniqueId(), mode);
            plugin.getSocket().sendPacket(setGamemode);
        } else if (sender instanceof ConsoleCommandSender) {
            sender.sendMessage(Prefixes.GAMEMODE + Messages.consoleNotImplemented);
        }
    }
}