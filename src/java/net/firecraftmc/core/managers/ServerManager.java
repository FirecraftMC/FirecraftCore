package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.enums.Rank;
import net.firecraftmc.shared.classes.enums.ServerType;
import net.firecraftmc.shared.classes.interfaces.IServerManager;
import net.firecraftmc.shared.classes.model.player.FirecraftPlayer;
import net.firecraftmc.shared.classes.model.server.FirecraftServer;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ServerManager implements IServerManager {

    private FirecraftCore plugin;

    public ServerManager(FirecraftCore plugin) {
        this.plugin = plugin;
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