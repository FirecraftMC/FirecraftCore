package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.*;
import net.firecraftmc.shared.classes.utils.CmdUtils;
import net.firecraftmc.shared.classes.utils.MojangUtils;
import net.firecraftmc.shared.enums.Rank;
import net.firecraftmc.shared.exceptions.NicknameException;
import net.firecraftmc.shared.packets.staffchat.FPStaffChatResetNick;
import net.firecraftmc.shared.packets.staffchat.FPStaffChatSetNick;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class NickManager implements TabExecutor {
    private final FirecraftCore plugin;
    private final List<UUID> settingNick = new ArrayList<>();
    private final Map<UUID, FirecraftPlayer> confirmNick = new HashMap<>();
    private static final String prefix = "&d&l[Nickname] ";
    
    public NickManager(FirecraftCore plugin) {
        this.plugin = plugin;
    }
    
    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (cmd.getName().equalsIgnoreCase("nick")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cOnly players may use that command.");
                return true;
            }
            
            FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
            if (!Utils.checkFirecraftPlayer((Player) sender, player)) return true;
            if (!CmdUtils.checkArgCountExact(sender, args, 1)) return true;
            
            if (!(player.getMainRank().equals(Rank.VIP) || player.getMainRank().isEqualToOrHigher(Rank.TRIAL_ADMIN))) {
                player.sendMessage(prefix + "&cYou are not allowed to use the nickname command.");
                return true;
            }
            this.settingNick.add(player.getUniqueId());
            UUID uuid;
            try {
                uuid = MojangUtils.getUUIDFromName(args[0]);
            } catch (Exception e) {
                player.sendMessage(prefix + "&cCould not get the uuid for the nickname &d" + args[0]);
                this.settingNick.remove(player.getUniqueId());
                return true;
            }
            
            if (uuid == null) {
                player.sendMessage(prefix + "&cThat name cannot be linked to a valid uuid.");
                return true;
            }
            
            FirecraftPlayer nick = Utils.getPlayerFromDatabase(plugin.getDatabase(), plugin, uuid);
            
            if (nick == null) {
                player.sendMessage(prefix + "&cThere was an unforeseen error in getting that nickname.");
                return true;
            }
            
            if (nick.isOnline()) {
                player.sendMessage(prefix + "&cThe nickname that you provided cannot be used because it is currently online.");
                settingNick.remove(player.getUniqueId());
                return true;
            }
            
            if (nick.getMainRank().isHigher(player.getMainRank()) || nick.getMainRank().equals(player.getMainRank())) {
                player.sendMessage(prefix + "&cYou cannot use that nickname because the nickname's rank is equal to or higher than yours.");
                settingNick.remove(player.getUniqueId());
                return true;
            }
            
            if (Rank.isStaff(nick.getMainRank())) {
                player.sendMessage(prefix + "&cThe nickname you provided cannot be used because it is a staff member profile.");
                settingNick.remove(player.getUniqueId());
                return true;
            }
            
            confirmNick.put(player.getUniqueId(), nick);
            player.sendMessage(prefix + "&7You need to confirm the info for the nick.\nType &a/nickconfirm&7. To cancel type &c/nickcancel&7.");
            player.sendMessage(prefix + "&6Nickname Profile Info: " + nick.getName());
            if (nick.getMainRank().equals(Rank.PRIVATE)) {
                player.sendMessage(prefix + "&6Rank: " + nick.getMainRank().getBaseColor() + "Private");
            } else {
                player.sendMessage(prefix + "&6Rank: " + nick.getMainRank().getPrefix());
            }
        } else if (cmd.getName().equalsIgnoreCase("nickrandom")) {
            if (sender instanceof Player) {
                FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
                if (!(player.getMainRank().equals(Rank.VIP) || player.getMainRank().isEqualToOrHigher(Rank.MOD))) {
                    player.sendMessage(prefix + "&cYou do not have permission to use the random nick command.");
                    return true;
                }
                
                player.sendMessage(prefix + "&cDue to the need for a rewrite of how players are stored, this command is disabled temporarily.");
                return true;
            } else {
                sender.sendMessage(prefix + "§cOnly Players may use that command.");
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("nickcancel")) {
            if (sender instanceof Player) {
                FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
                if (this.confirmNick.containsKey(player.getUniqueId()) || this.settingNick.contains(player.getUniqueId())) {
                    player.sendMessage(prefix + "&cYou have cancelled the nickname process.");
                    this.confirmNick.remove(player.getUniqueId());
                    this.settingNick.remove(player.getUniqueId());
                    return true;
                } else {
                    player.sendMessage(prefix + "&cYou are not currently setting a nickname.");
                    return true;
                }
            } else {
                sender.sendMessage("§cOnly players may use that command.");
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("nickconfirm")) {
            if (sender instanceof Player) {
                FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
                if (this.confirmNick.containsKey(player.getUniqueId())) {
                    FirecraftPlayer nick = this.confirmNick.get(player.getUniqueId());
                    try {
                        player.setNick(plugin, nick);
                    } catch (NicknameException e) {
                        player.sendMessage(prefix + "&cThere was an error setting the nickname.");
                        this.settingNick.remove(player.getUniqueId());
                        this.confirmNick.remove(player.getUniqueId());
                        return true;
                    }
                    
                    player.sendMessage(prefix + "&aSet your nickname to &b" + nick.getName());
                    this.settingNick.remove(player.getUniqueId());
                    this.confirmNick.remove(player.getUniqueId());
                    player.setActionBar(new ActionBar("&fYou are currently &cNICKED"));
                    FPStaffChatSetNick setNick = new FPStaffChatSetNick(plugin.getFirecraftServer(), player.getUniqueId(), nick.getName());
                    plugin.getSocket().sendPacket(setNick);
                } else {
                    player.sendMessage(prefix + "&cYou are not currently setting a nickname.");
                    return true;
                }
            } else {
                sender.sendMessage("§cOnly players may use that command.");
                return true;
            }
            
        } else if (cmd.getName().equalsIgnoreCase("unnick")) {
            if (sender instanceof ConsoleCommandSender) {
                sender.sendMessage("§cConsole can't set nicknames, so it can't reset nicknames");
                return true;
            }
            
            FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
            if (player.getNick() == null) {
                player.sendMessage(prefix + "&cYou do not have a nick currently set.");
                return true;
            }
            
            try {
                player.resetNick(plugin);
            } catch (NicknameException e) {
                player.sendMessage(prefix + "&cThere was an error resetting your nickname.");
                return true;
            }
            
            player.sendMessage(prefix + "&aYou have reset your nickname.");
            FPStaffChatResetNick resetNick = new FPStaffChatResetNick(plugin.getFirecraftServer(), player.getUniqueId());
            plugin.getSocket().sendPacket(resetNick);
        }
        return true;
    }
    
    public List<String> onTabComplete(CommandSender sender, Command cmd, String s, String[] args) {
        return null;
    }
}