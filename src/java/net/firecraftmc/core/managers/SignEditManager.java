package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.Prefixes;
import net.firecraftmc.shared.classes.model.FirecraftPlayer;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.classes.enums.Rank;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

public class SignEditManager implements CommandExecutor,Listener {
    
    private FirecraftCore plugin;
    private Map<Location, String[]> signChanges = new HashMap<>();

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

    @EventHandler
    public void onSignChange(SignChangeEvent e) {
        String[] lines = e.getLines();
        for (int i=0; i<lines.length; i++) {
            e.setLine(i, ChatColor.translateAlternateColorCodes('&', lines[i]));
        }
    }
    
    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (sender instanceof Player) {
            FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
        
            if (!player.getMainRank().isEqualToOrHigher(Rank.MODERATOR)) {
                player.sendMessage(Prefixes.SIGN_EDIT + Messages.noPermission);
                return true;
            }
        
            if (!(args.length > 0)) {
                player.sendMessage(Prefixes.SIGN_EDIT + Messages.notEnoughArgs);
                return true;
            }
        
            Sign sign;
        
            try {
                sign = (Sign) player.getPlayer().getTargetBlock(null, 100).getState();
            } catch (Exception e) {
                player.sendMessage(Prefixes.SIGN_EDIT + Messages.notLookingAtSign);
                return true;
            }
        
            if (sign == null) {
                player.sendMessage(Prefixes.SIGN_EDIT + Messages.notLookingAtSign);
                return true;
            }
        
            int line;
            try {
                line = Integer.parseInt(args[0])-1;
            } catch (NumberFormatException e) {
                player.sendMessage(Prefixes.SIGN_EDIT + Messages.invalidLineNumber);
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
                    player.sendMessage(Prefixes.SIGN_EDIT + Messages.noMoreThan16Char);
                    return true;
                }
            
                sign.setLine(line, text);
                sign.update();
                player.sendMessage(Prefixes.SIGN_EDIT + Messages.setLine((line+1) + "", text));
                this.signChanges.put(sign.getLocation(), sign.getLines());
            } else {
                sign.setLine(line, "");
                sign.update();
                player.sendMessage(Prefixes.SIGN_EDIT + Messages.setLine((line+1) + "", "a blank line"));
                this.signChanges.put(sign.getLocation(), sign.getLines());
            }
        
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendSignChange(sign.getLocation(), sign.getLines());
            }
        } else {
            sender.sendMessage(Prefixes.SIGN_EDIT + Messages.onlyPlayers);
            return true;
        }
        return true;
    }
}