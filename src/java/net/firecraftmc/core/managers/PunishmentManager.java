package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.*;
import net.firecraftmc.shared.classes.utils.MojangUtils;
import net.firecraftmc.shared.enforcer.Enforcer;
import net.firecraftmc.shared.enforcer.Type;
import net.firecraftmc.shared.enforcer.punishments.PermanentBan;
import net.firecraftmc.shared.enforcer.punishments.Punishment;
import net.firecraftmc.shared.enums.Rank;
import net.firecraftmc.shared.packets.FPacketPunish;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class PunishmentManager implements TabExecutor {
    private FirecraftCore plugin;
    private static final String prefix = "&d&l[ENFORCER] ";
    private final UUID firestar311 = UUID.fromString("3f7891ce-5a73-4d52-a2ba-299839053fdc");
    
    public PunishmentManager(FirecraftCore plugin) {
        this.plugin = plugin;
    }
    
    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (sender instanceof Player) {
            FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
            //TODO The direct commands will only be available to Admin+ when the automation part is implemented.
            if (!(args.length > 0)) {
                player.sendMessage(prefix + "&cYou must provide a name or uuid to punish.");
                return true;
            }
    
            UUID uuid;
            try {
                uuid = UUID.fromString(args[0]);
            } catch (Exception e) {
                uuid = MojangUtils.getUUIDFromName(args[0]);
            }
    
            if (uuid == null) {
                player.sendMessage(prefix + "&cThe name/uuid you provided is not valid.");
                return true;
            }
    
            FirecraftPlayer t = plugin.getPlayerManager().getPlayer(uuid);
            if (t == null) {
                t = Utils.getPlayerFromDatabase(plugin.getDatabase(), plugin, uuid);
                if (t == null) {
                    player.sendMessage(prefix + "&cCould not find a profile by that name, currently only players that have joined can be punished.");
                    return true;
                }
            }
    
            if (t.getMainRank().isEqualToOrHigher(player.getMainRank())) {
                if (!player.getUniqueId().equals(firestar311)) {
                    player.sendMessage(prefix + "&cYou cannot punish a player that is equal to or higher than your rank.");
                    return true;
                }
            }
            
            if (!(args.length > 1)) {
                player.sendMessage(prefix + "&cYou must provide a reason for the punishment.");
                return true;
            }
            
            StringBuilder reasonBuilder = new StringBuilder();
            for (int i=1; i<args.length; i++) {
                reasonBuilder.append(args[i]);
                if (!(i == args.length-1)) {
                    reasonBuilder.append(" ");
                }
            }
            
            String reason = reasonBuilder.toString();
            long date = System.currentTimeMillis();
            String punisher = player.getUniqueId().toString().replace("-", "");
            String target = t.getUniqueId().toString().replace("-", "");
            FirecraftServer server = plugin.getFirecraftServer();
            if (cmd.getName().equalsIgnoreCase("ban")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                    player.sendMessage(prefix + "&cOnly Admins+ can permanently ban a player.");
                    return true;
                }
    
                Type type = Type.BAN;
                PermanentBan permBan = new PermanentBan(type, server.getName(), punisher, target, reason, date);
                permBan.setActive(true);
                Punishment permanentBan = Enforcer.addToDatabase(plugin.getDatabase(), permBan);
                if (permanentBan != null) {
                    FPacketPunish punish = new FPacketPunish(server, permanentBan.getId());
                    plugin.getSocket().sendPacket(punish);
                } else {
                    player.sendMessage(prefix + "&cThere was an issue creating the punishment.");
                    return true;
                }
            } else if (cmd.getName().equalsIgnoreCase("tempban")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.MOD)) {
                    player.sendMessage(prefix + "&cOnly Mods+ can tempban a player.");
                    return true;
                }
            } else if (cmd.getName().equalsIgnoreCase("ipban")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.HEAD_ADMIN)) {
                    player.sendMessage(prefix + "&cOnly Head Admins+ can IP-ban a player.");
                    return true;
                }
            } else if (cmd.getName().equalsIgnoreCase("mute")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.MOD)) {
                    player.sendMessage(prefix + "&cOnly Mods+ can permanently mute a player.");
                    return true;
                }
            } else if (cmd.getName().equalsIgnoreCase("tempmute")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.MOD)) {
                    player.sendMessage(prefix + "&cOnly Helpers+ can tempmute a player.");
                    return true;
                }
            } else if (cmd.getName().equalsIgnoreCase("jail")) {
                if (!player.getMainRank().equals(Rank.HELPER)) {
                    player.sendMessage(prefix + "&cOnly Helpers can jail a player. If you are a Mod+, use the actual punishment.");
                    return true;
                }
            } else if (cmd.getName().equalsIgnoreCase("setjail")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                    player.sendMessage(prefix + "&cOnly Admins+ can set the jail location.");
                    return true;
                }
            } else if (cmd.getName().equalsIgnoreCase("kick")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.HELPER)) {
                    player.sendMessage(prefix + "&cOnly Helpers+ can kick a player.");
                    return true;
                }
            } else if (cmd.getName().equalsIgnoreCase("warn")) {
                if (!player.getMainRank().isEqualToOrHigher(Rank.HELPER)) {
                    player.sendMessage(prefix + "&cOnly Helpers+ can warn a player.");
                    return true;
                }
            }
        } else {
            sender.sendMessage(prefix + "§cNot implemented yet.");
        }
        
        
        return true;
    }
    
    public List<String> onTabComplete(CommandSender sender, Command cmd, String s, String[] args) {
        return null;
    }
}