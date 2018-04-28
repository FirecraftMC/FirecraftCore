package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.FirecraftPlayer;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.enums.Channel;
import net.firecraftmc.shared.enums.Rank;
import net.firecraftmc.shared.packets.staffchat.FPStaffChatMessage;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.List;

public class ChatManager implements TabExecutor,Listener {
    private final FirecraftCore plugin;

    private static final String prefix = "&d&l[Chat] ";
    
    public ChatManager(FirecraftCore plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChat(AsyncPlayerChatEvent e) {
        e.setCancelled(true);
        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(e.getPlayer().getUniqueId());
        if (player == null) {
            e.getPlayer().sendMessage(prefix + "§cYour player data has not been received yet, you are not allowed to speak.");
            return;
        }
        
        if (player.getChannel().equals(Channel.GLOBAL)) {
            if (player.isVanished()) {
                player.sendMessage(prefix + "&cYou are not allowed to talk in global while vanished.");
                return;
            }
            String format = Utils.Chat.formatGlobal(player, e.getMessage());
            for (FirecraftPlayer op : plugin.getPlayerManager().getPlayers()) {
                op.sendMessage(format);
            }
        } else if (player.getChannel().equals(Channel.STAFF)) {
            FPStaffChatMessage msg = new FPStaffChatMessage(plugin.getFirecraftServer(), player.getUniqueId(), e.getMessage());
            plugin.getSocket().sendPacket(msg);
        }
    }
    
    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (sender instanceof ConsoleCommandSender) {
            sender.sendMessage("§cConsole cannot use the chat command.");
            return true;
        } else if (sender instanceof Player) {
            FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
        
            if (!Utils.checkFirecraftPlayer((Player) sender, player)) return true;
            if (!Utils.Command.checkArgCountExact(sender, args, 1)) return true;
            if (Utils.Command.checkCmdAliases(args, 0, "staff", "st", "s")) {
                if (!Rank.isStaff(player.getMainRank())) {
                    player.sendMessage(prefix + "&cOnly staff members may use the staff chat channel.");
                    return true;
                }
            
                if (player.getChannel().equals(Channel.STAFF)) {
                    player.sendMessage(prefix + "&cYou are already speaking in that channel.");
                    return true;
                }
                player.setChannel(Channel.STAFF);
                player.sendMessage(prefix + "&aYou are now speaking in " + Channel.STAFF.getColor() + "&lStaff");
            } else if (Utils.Command.checkCmdAliases(args, 0, "global", "gl", "g")) {
                if (player.getChannel().equals(Channel.GLOBAL)) {
                    player.sendMessage(prefix + "&cYou are already speaking in that channel.");
                    return true;
                }
                player.setChannel(Channel.GLOBAL);
                player.sendMessage(prefix + "&aYou are now speaking in " + Channel.GLOBAL.getColor() + "&lGlobal");
            } else {
                player.sendMessage(prefix + "&cSupport for other channels is currently not implemented.");
                return true;
            }
        }
        return true;
    }
    
    public List<String> onTabComplete(CommandSender sender, Command cmd, String s, String[] args) {
        return null;
    }
}