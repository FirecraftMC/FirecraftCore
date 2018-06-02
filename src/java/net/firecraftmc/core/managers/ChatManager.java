package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.FirecraftPlayer;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.classes.enums.Channel;
import net.firecraftmc.shared.classes.enums.Rank;
import net.firecraftmc.shared.packets.staffchat.FPStaffChatMessage;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.sql.ResultSet;

public class ChatManager implements CommandExecutor,Listener {
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
            e.getPlayer().sendMessage(prefix + Messages.chatNoData);
            return;
        }
        
        if (!plugin.isWarnAcknowledged(player.getUniqueId())) {
            if (e.getMessage().equals(plugin.getAckCode(player.getUniqueId()))) {
                player.sendMessage(Messages.acknowledgeWarning);
                plugin.acknowledgeWarn(player.getUniqueId(), player.getName());
                return;
            } else {
                player.sendMessage(Messages.chatUnAckWarning);
                return;
            }
        }
    
        ResultSet muteSet = plugin.getFCDatabase().querySQL("SELECT * FROM `punishments` WHERE (`type`='MUTE' OR `type`='TEMP_MUTE') AND `active`='true';");
        try {
            if (muteSet.next()) {
                player.sendMessage(Messages.chatMuted);
                return;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    
        ResultSet jailSet = plugin.getFCDatabase().querySQL("SELECT * FROM `punishments` WHERE `target`='{uuid}' AND `active`='true' AND `type`='JAIL';".replace("{uuid}", player.getUniqueId().toString().replace("-", "")));
        try {
            if (jailSet.next()) {
                player.sendMessage(Messages.chatJailed);
                return;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        
        if (player.getChannel().equals(Channel.GLOBAL)) {
            if (player.isVanished() && !player.getVanishInfo().canChat()) {
                player.sendMessage(prefix + Messages.noTalkGlobal);
                return;
            }
            String format = Utils.Chat.formatGlobal(player, e.getMessage());
            if (format.toLowerCase().contains("[item]")) {
                if (player.getInventory().getItemInMainHand() != null && player.getInventory().getItemInMainHand().getType() != Material.AIR) {
                    String itemName = player.getInventory().getItemInMainHand().getItemMeta().getDisplayName();
                    format = format.replace("[item]", itemName);
                }
            }
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
            sender.sendMessage(Messages.noPermission);
            return true;
        } else if (sender instanceof Player) {
            FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
        
            if (!Utils.checkFirecraftPlayer((Player) sender, player)) return true;
            if (!Utils.Command.checkArgCountExact(sender, args, 1)) return true;
            if (Utils.Command.checkCmdAliases(args, 0, "staff", "st", "s")) {
                if (!Rank.isStaff(player.getMainRank())) {
                    player.sendMessage(prefix + Messages.onlyStaff);
                    return true;
                }
            
                if (player.getChannel().equals(Channel.STAFF)) {
                    player.sendMessage(prefix + Messages.alreadyInChannel);
                    return true;
                }
                player.setChannel(Channel.STAFF);
                player.sendMessage(prefix + Messages.channelSwitch(Channel.STAFF));
            } else if (Utils.Command.checkCmdAliases(args, 0, "global", "gl", "g")) {
                if (player.getChannel().equals(Channel.GLOBAL)) {
                    player.sendMessage(prefix + Messages.alreadyInChannel);
                    return true;
                }
                player.setChannel(Channel.GLOBAL);
                player.sendMessage(prefix + Messages.channelSwitch(Channel.GLOBAL));
            } else {
                player.sendMessage(prefix + Messages.noOtherChannels);
                return true;
            }
        }
        return true;
    }
}