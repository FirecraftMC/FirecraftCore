package net.firecraftmc.core.managers;

import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.util.*;
import net.firecraftmc.core.FirecraftCore;
import org.bukkit.*;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

public class SignEditManager implements Listener {
    
    private final FirecraftCore plugin;
    private final Map<Location, String[]> signChanges = new HashMap<>();

    public SignEditManager(FirecraftCore plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    
        FirecraftCommand signEdit = new FirecraftCommand("signedit", "Edits a line on a sign") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (!(args.length > 0)) {
                    player.sendMessage(Prefixes.SIGN_EDIT + Messages.notEnoughArgs);
                    return;
                }
    
                Sign sign;
    
                try {
                    sign = (Sign) player.getPlayer().getTargetBlock(null, 100).getState();
                } catch (Exception e) {
                    player.sendMessage(Prefixes.SIGN_EDIT + Messages.notLookingAtSign);
                    return;
                }
    
                if (sign == null) {
                    player.sendMessage(Prefixes.SIGN_EDIT + Messages.notLookingAtSign);
                    return;
                }
    
                int line;
                try {
                    line = Integer.parseInt(args[0])-1;
                } catch (NumberFormatException e) {
                    player.sendMessage(Prefixes.SIGN_EDIT + Messages.invalidLineNumber);
                    return;
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
                        return;
                    }
        
                    sign.setLine(line, text);
                    sign.update();
                    player.sendMessage(Prefixes.SIGN_EDIT + Messages.setLine((line+1) + "", text));
                    signChanges.put(sign.getLocation(), sign.getLines());
                } else {
                    sign.setLine(line, "");
                    sign.update();
                    player.sendMessage(Prefixes.SIGN_EDIT + Messages.setLine((line+1) + "", "a blank line"));
                    signChanges.put(sign.getLocation(), sign.getLines());
                }
    
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendSignChange(sign.getLocation(), sign.getLines());
                }
            }
        }.setBaseRank(Rank.MOD);
        
        plugin.getCommandManager().addCommand(signEdit);
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
}