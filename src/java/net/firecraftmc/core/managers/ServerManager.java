package net.firecraftmc.core.managers;

import net.firecraftmc.api.FirecraftAPI;
import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.enums.ServerType;
import net.firecraftmc.api.interfaces.IServerManager;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.model.server.FirecraftServer;
import net.firecraftmc.api.util.Messages;
import net.firecraftmc.api.util.Utils;
import net.firecraftmc.core.FirecraftCore;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class ServerManager implements IServerManager {
    
    private final FirecraftCore plugin;
    
    public ServerManager(FirecraftCore plugin) {
        this.plugin = plugin;
        
        FirecraftCommand server = new FirecraftCommand("firecraftserver", "Firecraft Server management command.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (!(args.length > 1)) {
                    player.sendMessage("<ec>Invalid amount of arguments: /firecraftserver <create|c|set|s <arguments>");
                    return;
                }
                
                if (Utils.Command.checkCmdAliases(args, 0, "create", "c")) {
                    if (args.length != 5) {
                        player.sendMessage("<ec>Invalid amount of arugments: /firecraftserver create|c <id|random|r> <name> <colorcode|chatcolor> <type>");
                        return;
                    }
    
                    UUID uuid = null;
                    if (Utils.Command.checkCmdAliases(args, 1, "random", "r")) {
                        uuid = UUID.randomUUID();
                        while (plugin.getFCServer(uuid.toString()) != null) {
                            uuid = UUID.randomUUID();
                        }
                    } else {
                        try {
                            uuid = UUID.fromString(args[1]);
                            if (plugin.getFCServer(uuid.toString()) != null) {
                                player.sendMessage("<ec>A server with that id already exists.");
                                return;
                            }
                        } catch (Exception e) {
                            player.sendMessage("<ec>You have supplied an invalid id.");
                        }
                    }
                    
                    if (uuid == null) {
                        player.sendMessage("<ec>There was an issue with the id of the server, unable to continue.");
                        return;
                    }
                    
                    String name = args[2];
    
                    ChatColor color;
                    try {
                        color = ChatColor.valueOf(args[3]);
                    } catch (Exception e) {
                        player.sendMessage("<ec>You provided an invalid color name.");
                        return;
                    }
    
                    ServerType type;
                    try {
                        type = ServerType.valueOf(args[4]);
                    } catch (Exception e) {
                        player.sendMessage("<ec>You provided an invalid type.");
                        return;
                    }
                    
                    FirecraftServer createdServer = new FirecraftServer(uuid.toString(), name, color, type);
                    plugin.getFCDatabase().saveServer(createdServer);
                    player.sendMessage("<nc>Created a server with the name <vc>" + name);
                } else if (Utils.Command.checkCmdAliases(args, 0, "set", "s")) {
                
                }
            }
        }.addAlias("fcs").setBaseRank(Rank.FIRECRAFT_TEAM);
        
        plugin.getCommandManager().addCommand(server);
    }
    
    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Messages.onlyPlayers);
            return true;
        }
        
        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
        if (!player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
            player.sendMessage("<ec>Only members of The Firecraft Team can use that command.");
            return true;
        }
        
        if (!(args.length > 0)) {
            player.sendMessage("<ec>Invalid amount of arguments");
            return true;
        }
        
        if (args[0].equalsIgnoreCase("create")) {
            if (!(args.length == 5)) {
                player.sendMessage("<ec>Invalid amount of arguments: /<label> <create|c> <id> <name> <color> <type>".replace("<label>", s));
                return true;
            }
            
            ChatColor color = ChatColor.valueOf(args[3]);
            ServerType type = ServerType.valueOf(args[4]);
            
            FirecraftServer server = new FirecraftServer(args[1], args[2], color, type);
            String ip = plugin.getSocket().getJavaSocket().getLocalAddress().toString(); //TODO Might not work as expected
            server.setIp(ip.replace("/", ""));
            
            plugin.getFCDatabase().saveServer(server);
            plugin.setServer(server);
            player.sendMessage("<nc>Created a server with the id <vc>" + server.getId());
        }
        
        return true;
    }
    
    public FirecraftServer getServer(String id) {
        return plugin.getFCDatabase().getServer(id);
    }
}