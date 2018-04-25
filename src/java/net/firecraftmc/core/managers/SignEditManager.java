package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.FirecraftPlayer;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.enums.Rank;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Sign;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

public class SignEditManager implements CommandExecutor,Listener {
    
    private FirecraftCore plugin;
    private Map<Location, String[]> signChanges = new HashMap<>();
    
    private static final String prefix = "&d&l[SignEdit] ";
    
    public SignEditManager(FirecraftCore plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        new BukkitRunnable() {
            public void run() {
                for (Location loc : signChanges.keySet()) {
                    e.getPlayer().sendSignChange(loc, signChanges.get(loc));
                }
            }
        }.runTaskLater(plugin, 5L);
    }
    
    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (sender instanceof Player) {
            FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
        
            if (!player.getMainRank().isEqualToOrHigher(Rank.MOD)) {
                player.sendMessage(prefix + "&cOnly Mods+ can edit signs.");
                return true;
            }
        
            if (!(args.length > 0)) {
                player.sendMessage(prefix + "&cYou do not have enough arguments.");
                return true;
            }
        
            Sign sign;
        
            try {
                sign = (Sign) player.getPlayer().getTargetBlock(null, 100).getState();
            } catch (Exception e) {
                player.sendMessage(prefix + "&cYou are not looking at a sign.");
                return true;
            }
        
            if (sign == null) {
                player.sendMessage(prefix + "&cYou are not looking at a sign.");
                return true;
            }
        
            int line;
            try {
                line = Integer.parseInt(args[0])-1;
            } catch (NumberFormatException e) {
                player.sendMessage(prefix + "&cYou provided an invalid number for the line to set.");
                return true;
            }
        
            if (args.length > 1) {
                StringBuilder sb = new StringBuilder();
                for (int i=1; i<args.length; i++) {
                    sb.append(args[i]);
                    if (!(i == args.length-1)) {
                        sb.append(" ");
                    }
                }
            
                String text = sb.toString();
                text = Utils.color(text);
                if (text.length() > 16) {
                    player.sendMessage(prefix + "&cSigns cannot have more than 16 characters per line.");
                    return true;
                }
            
                sign.setLine(line, text);
                sign.update();
                player.sendMessage(prefix + "&aSet line &b" + (line+1) + " &ato &b" + text);
                this.signChanges.put(sign.getLocation(), sign.getLines());
            } else {
                sign.setLine(line, "");
                sign.update();
                player.sendMessage(prefix + "&aSet line &b" + (line+1) + " &ato a blank line.");
                this.signChanges.put(sign.getLocation(), sign.getLines());
            }
        
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendSignChange(sign.getLocation(), sign.getLines());
            }
        
        } else {
            sender.sendMessage(prefix + "§cOnly players may use that command.");
            return true;
        }
        return true;
    }
}