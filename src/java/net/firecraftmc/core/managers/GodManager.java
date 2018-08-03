package net.firecraftmc.core.managers;

import net.firecraftmc.api.FirecraftAPI;
import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.core.FirecraftCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

public class GodManager implements Listener {
    
    public GodManager(FirecraftCore plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    
        FirecraftCommand god = new FirecraftCommand("god", "Toggle your god status") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (player.hasRank(Rank.MODERATOR, Rank.TRIAL_MOD)) {
                    player.sendMessage("<ec>Please use staff mode instead of this command.");
                    return;
                }
                
                player.setGod(!player.isGodEnabled());
                player.sendMessage("<nc>You have toggled god mode to <vc>" + player.isGodEnabled());
            }
        }.setBaseRank(Rank.FAMOUS);
    }
    
    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player) {
            FirecraftPlayer player = FirecraftAPI.getPlayer(e.getEntity().getUniqueId());
            assert player != null;
            if (player.isGodEnabled()) e.setCancelled(true);
        }
    }
}