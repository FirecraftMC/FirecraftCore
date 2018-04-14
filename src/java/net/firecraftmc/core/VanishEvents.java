package net.firecraftmc.core;

import net.firecraftmc.shared.classes.FirecraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;

public class VanishEvents implements Listener {
    
    private FirecraftCore plugin;
    
    public VanishEvents(FirecraftCore plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        FirecraftPlayer player = plugin.getFirecraftPlayer(e.getPlayer().getUniqueId());
        if (player.isVanished()) {
            System.out.println("Vanished");
            if (!player.getVanishInfo().blockBreak()) {
                e.setCancelled(true);
                player.sendMessage("&cYou cannot break blocks while vanished.");
            }
        }
    }
    
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        FirecraftPlayer player = plugin.getFirecraftPlayer(e.getPlayer().getUniqueId());
        if (player.isVanished()) {
            if (!player.getVanishInfo().blockPlace()) {
                e.setCancelled(true);
                player.sendMessage("&cYou cannot place blocks while vanished.");
            }
        }
    }
    
    @EventHandler
    public void onItemPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player) {
            FirecraftPlayer player = plugin.getFirecraftPlayer(e.getEntity().getUniqueId());
            if (player.isVanished()) {
                if (!player.getVanishInfo().blockPlace()) {
                    e.setCancelled(true);
                    player.sendMessage("&cYou cannot pickup items while vanished.");
                }
            }
        }
    }
    
    @EventHandler
    public void onPlayerOpenInventory(InventoryOpenEvent e) {
        FirecraftPlayer player = plugin.getFirecraftPlayer(e.getPlayer().getUniqueId());
        if (player.isVanished()) {
            if (!player.getVanishInfo().blockPlace()) {
                e.setCancelled(true);
                player.sendMessage("&cYou cannot open inventories while vanished.");
            } else {
                if (player.getVanishInfo().silentInventoryOpen()) {
                    e.setCancelled(true);
                    player.getPlayer().openInventory(e.getInventory()); //TODO MIGHT NOT STOP ANIMATION OF OPENING CHESTS
                }
            }
        }
    }
    
    
    @EventHandler
    public void onEntityDamageEvent(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player) {
            FirecraftPlayer player = plugin.getFirecraftPlayer(e.getDamager().getUniqueId());
            if (player.isVanished()) {
                if (!player.getVanishInfo().entityInteract()) {
                    e.setCancelled(true);
                    player.sendMessage("&cYou cannot damage entities while vanished.");
                }
            }
        }
    }
    
    @EventHandler
    public void onPlayerBucketFill(PlayerBucketFillEvent e) {
        FirecraftPlayer player = plugin.getFirecraftPlayer(e.getPlayer().getUniqueId());
        if (player.isVanished()) {
            if (!player.getVanishInfo().itemUse()) {
                e.setCancelled(true);
                player.sendMessage("&cYou cannot fill buckets while vanished.");
            }
        }
    }
    
    @EventHandler
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent e) {
        FirecraftPlayer player = plugin.getFirecraftPlayer(e.getPlayer().getUniqueId());
        if (player.isVanished()) {
            if (!player.getVanishInfo().itemUse()) {
                e.setCancelled(true);
                player.sendMessage("&cYou cannot empty buckets while vanished.");
            }
        }
    }
    
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent e) {
        FirecraftPlayer player = plugin.getFirecraftPlayer(e.getPlayer().getUniqueId());
        if (player.isVanished()) {
            if (!player.getVanishInfo().itemPickup()) {
                e.setCancelled(true);
                player.sendMessage("&cYou cannot drop items while vanished.");
            }
        }
    }
    
    @EventHandler
    public void onPlayerEntityInteract(PlayerInteractEntityEvent e) {
        FirecraftPlayer player = plugin.getFirecraftPlayer(e.getPlayer().getUniqueId());
        if (player.isVanished()) {
            if (!player.getVanishInfo().entityInteract()) {
                e.setCancelled(true);
                player.sendMessage("&cYou cannot interact with entities while vanished.");
            }
        }
    }
    
    @EventHandler
    public void onPlayerEntityShear(PlayerShearEntityEvent e) {
        FirecraftPlayer player = plugin.getFirecraftPlayer(e.getPlayer().getUniqueId());
        if (player.isVanished()) {
            if (!player.getVanishInfo().itemPickup()) {
                e.setCancelled(true);
                player.sendMessage("&cYou cannot shear entities while vanished.");
            }
        }
    }
}