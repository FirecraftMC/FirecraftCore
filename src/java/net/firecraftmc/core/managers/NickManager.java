package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.*;
import net.firecraftmc.shared.classes.utils.CmdUtils;
import net.firecraftmc.shared.classes.utils.MojangUtils;
import net.firecraftmc.shared.enums.Rank;
import net.firecraftmc.shared.exceptions.NicknameException;
import net.firecraftmc.shared.packets.FPRequestProfile;
import net.firecraftmc.shared.packets.staffchat.FPStaffChatResetNick;
import net.firecraftmc.shared.packets.staffchat.FPStaffChatSetNick;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class NickManager implements TabExecutor {
    private final FirecraftCore plugin;
    private final List<UUID> settingNick = new ArrayList<>();
    private final Map<UUID, FirecraftPlayer> confirmNick = new HashMap<>();
    
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
        
            if (!(player.getMainRank().equals(Rank.VIP) || player.getMainRank().equals(Rank.TRIAL_ADMIN) || player.getMainRank().isHigher(Rank.TRIAL_ADMIN))) {
                player.sendMessage("&cYou are not allowed to use the nickname command.");
                return true;
            }
            this.settingNick.add(player.getUniqueId());
            UUID uuid;
            try {
                uuid = MojangUtils.getUUIDFromName(args[0]);
            } catch (Exception e) {
                player.sendMessage("&cCould not get the uuid for the nickname &d" + args[0]);
                this.settingNick.remove(player.getUniqueId());
                return true;
            }
        
            if (plugin.getPlayerManager().getPlayer(uuid) != null) {
                player.sendMessage("&cThe nickname that you provided cannot be used because it is currently online.");
                settingNick.remove(player.getUniqueId());
                return true;
            }
        
            if (plugin.getPlayerManager().getProfile(uuid) == null) {
                FPRequestProfile profileRequest = new FPRequestProfile(uuid);
                plugin.getSocket().sendPacket(profileRequest);
                new BukkitRunnable() {
                    public void run() {
                        FirecraftPlayer profile = plugin.getPlayerManager().getProfile(uuid);
                        if (profile != null) {
                            cancel();
                            if (profile.getMainRank().isHigher(player.getMainRank()) || profile.getMainRank().equals(player.getMainRank())) {
                                player.sendMessage("&cYou cannot use that nickname because the nickname's rank is equal to or higher than yours.");
                                settingNick.remove(player.getUniqueId());
                                return;
                            }
                        
                            if (profile.isOnline()) {
                                player.sendMessage("&cThe nickname that you provided cannot be used because it is currently online.");
                                settingNick.remove(player.getUniqueId());
                                return;
                            }
                        
                            confirmNick.put(player.getUniqueId(), profile);
                            //TODO Add chat click support for the confirm or cancel commands
                            player.sendMessage("&7You need to confirm the info for the nick.\nType &a/nickconfirm&7. To cancel type &c/nickcancel&7.");
                            player.sendMessage("&6Nickname Profile Info: " + profile.getName());
                            player.sendMessage("&6Rank: " + profile.getMainRank().getPrefix());
                            //TODO Print out stats when those are implemented
                        }
                    }
                }.runTaskTimerAsynchronously(plugin, 0L, 10L);
            } else {
                FirecraftPlayer profile = plugin.getPlayerManager().getProfile(uuid);
                player.sendMessage("&7You need to confirm the info for the nick.\nType &a/nickconfirm&7. To cancel type &c/nickcancel&7.");
                player.sendMessage("&6Nickname Profile Info: " + profile.getName());
                if (profile.getMainRank().equals(Rank.PRIVATE)) {
                    player.sendMessage("&6Rank: " + profile.getMainRank().getBaseColor() + "Default");
                } else {
                    player.sendMessage("&6Rank: " + profile.getMainRank().getPrefix());
                }
                //TODO Print out stats when those are implemented
                this.confirmNick.put(player.getUniqueId(), plugin.getPlayerManager().getProfile(uuid));
            }
        } else if (cmd.getName().equalsIgnoreCase("nickcancel")) {
            if (sender instanceof Player) {
                FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
                if (this.confirmNick.containsKey(player.getUniqueId()) || this.settingNick.contains(player.getUniqueId())) {
                    player.sendMessage("&cYou have cancelled the nickname process.");
                    this.confirmNick.remove(player.getUniqueId());
                    this.settingNick.remove(player.getUniqueId());
                    return true;
                } else {
                    player.sendMessage("&cYou are not currently setting a nickname.");
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
                        player.sendMessage("&cThere was an error setting the nickname.");
                        this.settingNick.remove(player.getUniqueId());
                        this.confirmNick.remove(player.getUniqueId());
                        return true;
                    }
                
                    player.sendMessage("&aSet your nickname to &b" + nick.getName());
                    this.settingNick.remove(player.getUniqueId());
                    this.confirmNick.remove(player.getUniqueId());
                    player.setActionBar(new ActionBar("&fYou are currently &cNICKED"));
                    FPStaffChatSetNick setNick = new FPStaffChatSetNick(plugin.getFirecraftServer(), player, nick);
                    plugin.getSocket().sendPacket(setNick);
                } else {
                    player.sendMessage("&cYou are not currently setting a nickname.");
                    return true;
                }
            } else {
                sender.sendMessage("§cOnly players may use that command.");
                return true;
            }
        
        } else if (cmd.getName().equalsIgnoreCase("nickreset")) {
            if (sender instanceof ConsoleCommandSender) {
                sender.sendMessage("§cConsole can't set nicknames, so it can't reset nicknames");
                return true;
            }
        
            FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
            if (player.getNick() == null) {
                player.sendMessage("&cYou do not have a nick currently set.");
                return true;
            }
        
            try {
                player.resetNick(plugin);
            } catch (NicknameException e) {
                player.sendMessage("&cThere was an error resetting your nickname.");
                return true;
            }
        
            player.sendMessage("&aYou have reset your nickname.");
            FPStaffChatResetNick resetNick = new FPStaffChatResetNick(plugin.getFirecraftServer(), player);
            plugin.getSocket().sendPacket(resetNick);
        }
        return true;
    }
    
    public List<String> onTabComplete(CommandSender sender, Command cmd, String s, String[] args) {
        return null;
    }
}