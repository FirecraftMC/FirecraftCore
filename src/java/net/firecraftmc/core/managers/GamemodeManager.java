package net.firecraftmc.core.managers;

import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.enums.ServerType;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.model.server.FirecraftServer;
import net.firecraftmc.api.packets.staffchat.*;
import net.firecraftmc.api.util.*;
import net.firecraftmc.core.FirecraftCore;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class GamemodeManager implements Listener {
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
        
        FirecraftCommand gamemode = new FirecraftCommand("gamemode", "Base command for changing gamemodes") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (args.length > 0) {
                    GameMode mode = getGameMode(args);
                    
                    if (mode == null) {
                        player.sendMessage(Prefixes.GAMEMODE + Messages.invalidGamemode);
                        return;
                    }
                    
                    gamemodeShortcut(player, mode, args, false);
                }
            }
        };
        gamemode.addAlias("gm").setBaseRank(Rank.TRIAL_ADMIN).addRank(Rank.BUILD_TEAM);
        
        FirecraftCommand gmc = new FirecraftCommand("gmc", "Quick access gamemode creative command") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                gamemodeShortcut(player, GameMode.CREATIVE, args, true);
            }
        };
        gmc.setBaseRank(Rank.TRIAL_ADMIN).addRank(Rank.BUILD_TEAM);
        
        FirecraftCommand gms = new FirecraftCommand("gms", "Quick access gamemode survival command") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                gamemodeShortcut(player, GameMode.SURVIVAL, args, true);
            }
        };
        gms.setBaseRank(Rank.TRIAL_ADMIN).addRank(Rank.BUILD_TEAM);
        
        FirecraftCommand gmsp = new FirecraftCommand("gmsp", "Quick access gamemode spectator command") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                gamemodeShortcut(player, GameMode.SPECTATOR, args, true);
            }
        };
        gmsp.setBaseRank(Rank.TRIAL_ADMIN).addRank(Rank.BUILD_TEAM);
        
        FirecraftCommand gma = new FirecraftCommand("gma", "Quick access gamemode adventure command") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                gamemodeShortcut(player, GameMode.ADVENTURE, args, true);
            }
        };
        gma.setBaseRank(Rank.TRIAL_ADMIN).addRank(Rank.BUILD_TEAM);
        
        
        plugin.getCommandManager().addCommands(gamemode, gmc, gms, gmsp, gma);
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
    
    private GameMode getGameMode(String[] args) {
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
        return mode;
    }
    
    private void gamemodeTarget(FirecraftPlayer player, GameMode mode, String targetName) {
        FirecraftPlayer target = plugin.getPlayerManager().getPlayer(targetName);
        if (target == null) {
            player.sendMessage(Prefixes.GAMEMODE + "<ec>That player does not exist.");
            return;
        }
        
        if (target.getPlayer() == null) {
            player.sendMessage(Prefixes.GAMEMODE + "<ec>That player is not online.");
            return;
        }
        
        if (target.getMainRank().isEqualToOrHigher(player.getMainRank())) {
            if (!(target.getMainRank().equals(Rank.FIRECRAFT_TEAM) && player.getMainRank().equals(Rank.FIRECRAFT_TEAM))) {
                player.sendMessage(Prefixes.GAMEMODE + "<ec>You cannot change the rank of a player that has the same rank or higher than you.");
                return;
            }
        }
        
        target.setGameMode(mode);
        target.sendMessage(Prefixes.GAMEMODE + "<nc>Your gamemode was changed to <vc>" + mode.toString().toLowerCase() + " <nc>by <vc>" + player.getName());
        FPSCSetGamemodeOthers setGamemodeOthers = new FPSCSetGamemodeOthers(plugin.getFCServer().getId(), player.getUniqueId(), mode, target.getUniqueId());
        plugin.getSocket().sendPacket(setGamemodeOthers);
    }
    
    private void gamemodeShortcut(FirecraftPlayer player, GameMode mode, String[] args, boolean sccmd) {
        if (!sccmd) {
            if (args.length == 1) {
                gamemodeSelf(player, mode);
            } else if (args.length == 2) {
                gamemodeTarget(player, mode, args[1]);
            }
        } else {
            if (args.length == 0) {
                gamemodeSelf(player, mode);
            } else if (args.length == 1) {
                gamemodeTarget(player, mode, args[0]);
            }
        }
    }
    
    private void gamemodeSelf(FirecraftPlayer player, GameMode mode) {
        if (player.getMainRank().equals(Rank.BUILD_TEAM)) {
            if (!plugin.getFCServer().getType().equals(ServerType.BUILD)) {
                player.sendMessage(Prefixes.GAMEMODE + "<ec>You are not allowed to use that gamemode on this server.");
            }
        } else {
            player.setGameMode(mode);
            FPSCSetGamemode setGamemode = new FPSCSetGamemode(plugin.getFCServer().getId(), player.getUniqueId(), mode);
            plugin.getSocket().sendPacket(setGamemode);
        }
    }
}