package net.firecraftmc.core.wrapper;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.FirecraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;

public class ItemPickupEvent1_12 implements Listener {
   
    private FirecraftCore plugin;
    
    public ItemPickupEvent1_12(FirecraftCore plugin) {
        this.plugin = plugin;
    }
    
    public void itemPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player) {
            FirecraftPlayer player = plugin.getPlayerManager().getPlayer(e.getEntity().getUniqueId());
            if (player.isVanished()) {
                if (!player.getVanishInfo().itemPickup()) {
                    e.setCancelled(true);
                }
            }
        }
    }
}