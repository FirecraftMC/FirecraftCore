package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.ActionBar;
import net.firecraftmc.shared.classes.FirecraftPlayer;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.enums.Rank;
import net.firecraftmc.shared.exceptions.NicknameException;
import net.firecraftmc.shared.packets.staffchat.FPStaffChatResetNick;
import net.firecraftmc.shared.packets.staffchat.FPStaffChatSetNick;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class NickManager implements CommandExecutor {
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
                sender.sendMessage(Messages.onlyPlayers);
                return true;
            }
            
            FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
            if (!Utils.checkFirecraftPlayer((Player) sender, player)) return true;
            if (!Utils.Command.checkArgCountExact(sender, args, 1)) return true;
            
            if (!(player.getMainRank().equals(Rank.VIP) || player.getMainRank().equals(Rank.FAMOUS) || player.getMainRank().isEqualToOrHigher(Rank.TRIAL_ADMIN))) {
                player.sendMessage(prefix + Messages.noPermission);
                return true;
            }
            this.settingNick.add(player.getUniqueId());
            UUID uuid;
            try {
                uuid = Utils.Mojang.getUUIDFromName(args[0]);
            } catch (Exception e) {
                player.sendMessage(prefix + Messages.uuidErrorNickname);
                this.settingNick.remove(player.getUniqueId());
                return true;
            }
            
            if (uuid == null) {
                player.sendMessage(prefix + Messages.uuidErrorNickname);
                return true;
            }
            
            FirecraftPlayer nick = Utils.Database.getPlayerFromDatabase(plugin.getFirecraftServer(), plugin.getDatabase(), uuid);
            
            if (nick == null) {
                player.sendMessage(prefix + Messages.getProfileError);
                return true;
            }
            
            if (nick.isOnline()) {
                player.sendMessage(prefix + Messages.nickNameOnline);
                settingNick.remove(player.getUniqueId());
                return true;
            }
            
            if (nick.getMainRank().isHigher(player.getMainRank()) || nick.getMainRank().equals(player.getMainRank())) {
                player.sendMessage(prefix + Messages.nickRankIsHigher);
                settingNick.remove(player.getUniqueId());
                return true;
            }
            
            if (Rank.isStaff(nick.getMainRank()) && !player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                player.sendMessage(prefix + Messages.nickIsStaff);
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
                    player.sendMessage(prefix + Messages.noPermission);
                    return true;
                }
                
                player.sendMessage(prefix + "&cDue to the need for a rewrite of how players are stored, this command is disabled temporarily.");
                return true;
            } else {
                sender.sendMessage(prefix + Messages.onlyPlayers);
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("nickcancel")) {
            if (sender instanceof Player) {
                FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
                if (this.confirmNick.containsKey(player.getUniqueId()) || this.settingNick.contains(player.getUniqueId())) {
                    player.sendMessage(prefix + Messages.nickNameCancelled);
                    this.confirmNick.remove(player.getUniqueId());
                    this.settingNick.remove(player.getUniqueId());
                    return true;
                } else {
                    player.sendMessage(prefix + Messages.notSettingNick);
                    return true;
                }
            } else {
                sender.sendMessage(Messages.onlyPlayers);
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
                        player.sendMessage(prefix + Messages.nickSettingError);
                        this.settingNick.remove(player.getUniqueId());
                        this.confirmNick.remove(player.getUniqueId());
                        return true;
                    }
                    
                    player.sendMessage(prefix + Messages.setNick(nick.getName()));
                    this.settingNick.remove(player.getUniqueId());
                    this.confirmNick.remove(player.getUniqueId());
                    player.setActionBar(new ActionBar(Messages.actionBar_Nicked));
                    FPStaffChatSetNick setNick = new FPStaffChatSetNick(plugin.getFirecraftServer(), player.getUniqueId(), nick.getName());
                    plugin.getSocket().sendPacket(setNick);
                } else {
                    player.sendMessage(prefix + Messages.notSettingNick);
                    return true;
                }
            } else {
                sender.sendMessage(Messages.onlyPlayers);
                return true;
            }
            
        } else if (cmd.getName().equalsIgnoreCase("unnick")) {
            if (sender instanceof ConsoleCommandSender) {
                sender.sendMessage(Messages.onlyPlayers);
                return true;
            }
            
            FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
            if (player.getNick() == null) {
                player.sendMessage(prefix + Messages.notSettingNick);
                return true;
            }
            
            try {
                player.resetNick(plugin);
            } catch (NicknameException e) {
                player.sendMessage(prefix + Messages.resetNickError);
                return true;
            }
            
            player.sendMessage(prefix + Messages.resetNickname);
            FPStaffChatResetNick resetNick = new FPStaffChatResetNick(plugin.getFirecraftServer(), player.getUniqueId());
            plugin.getSocket().sendPacket(resetNick);
        }
        return true;
    }
}