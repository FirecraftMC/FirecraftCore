package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.FirecraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryPickupItemEvent;

public class ItemPickupEvent1_8 implements Listener {
   
    private FirecraftCore plugin;
    
    public ItemPickupEvent1_8(FirecraftCore plugin) {
        this.plugin = plugin;
    }
    
    public void inventoryItemPickup(InventoryPickupItemEvent e) {
        if (e.getInventory().getHolder() instanceof Player) {
            Player p = (Player) e.getInventory().getHolder();
            FirecraftPlayer player = plugin.getPlayerManager().getPlayer(p.getUniqueId());
            if (player.isVanished()) {
                if (!player.getVanishInfo().itemPickup()) {
                    e.setCancelled(true);
                }
            }
        }
    }
}