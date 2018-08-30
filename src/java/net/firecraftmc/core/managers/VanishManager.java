package net.firecraftmc.core.managers;

import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.menus.VanishToggleMenu;
import net.firecraftmc.api.model.player.ActionBar;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.packets.staffchat.FPSCVanishToggle;
import net.firecraftmc.api.util.*;
import net.firecraftmc.api.vanish.VanishToggle;
import net.firecraftmc.core.FirecraftCore;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.ItemStack;

public class VanishManager implements Listener {
    
    private final FirecraftCore plugin;
    
    public VanishManager(FirecraftCore plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        plugin.getSocket().addSocketListener(packet -> {
            if (packet instanceof FPSCVanishToggle) {
                FPSCVanishToggle toggleVanish = ((FPSCVanishToggle) packet);
                FirecraftPlayer staffMember = plugin.getPlayerManager().getPlayer(toggleVanish.getPlayer());
                String format = Utils.Chat.formatVanishToggle(plugin.getServerManager().getServer(toggleVanish.getServerId()), staffMember, staffMember.isVanished());
                Utils.Chat.sendStaffChatMessage(plugin.getPlayerManager().getPlayers(), staffMember, format);
            }
        });
        
        FirecraftCommand vanish = new FirecraftCommand("vanish", "Toggle vanish status or other settings") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (args.length == 0) {
                    if (player.isVanished()) {
                        boolean flight = player.getVanishInfo().allowFlightBeforeVanish();
                        player.unVanish();
                        
                        for (FirecraftPlayer p : plugin.getPlayerManager().getPlayers()) {
                            p.getPlayer().showPlayer(player.getPlayer());
                            if (!player.isNicked()) {
                                player.getPlayer().setPlayerListName(player.getName());
                            } else {
                                player.getPlayer().setPlayerListName(player.getNick().getProfile().getName());
                            }
                            p.getScoreboard().updateScoreboard(p);
                        }
                        player.setActionBar(null);
                        player.getPlayer().setAllowFlight(flight);
                        player.updatePlayerListName();
                        plugin.getFCDatabase().updateVanish(player);
                    } else {
                        player.vanish();
                        for (FirecraftPlayer p : plugin.getPlayerManager().getPlayers()) {
                            if (!player.isNicked()) {
                                player.getPlayer().setPlayerListName(player.getName() + " §7§l[V]");
                            } else {
                                player.getPlayer().setPlayerListName(player.getNick().getProfile().getName() + "§7§l[V]");
                            }
                            
                            if (!p.getMainRank().isEqualToOrHigher(player.getMainRank())) {
                                p.getPlayer().hidePlayer(player.getPlayer());
                                p.getScoreboard().updateScoreboard(p);
                            }
                        }
                        
                        player.setActionBar(new ActionBar(Messages.actionBar_Vanished));
                        player.getVanishInfo().setAllowFlightBeforeVanish(player.getPlayer().getAllowFlight());
                        player.getPlayer().setAllowFlight(true);
                    }
                    FPSCVanishToggle toggleVanish = new FPSCVanishToggle(plugin.getFCServer().getId(), player.getUniqueId());
                    plugin.getSocket().sendPacket(toggleVanish);
                    plugin.getFCDatabase().updateVanish(player);
                } else {
                    if (!Utils.Command.checkCmdAliases(args, 0, "settings", "s")) {
                        player.sendMessage(Prefixes.VANISH + Messages.invalidSubCommand);
                        return;
                    }
                    
                    if (!player.isVanished()) {
                        player.sendMessage(Prefixes.VANISH + Messages.notVanished);
                        return;
                    }
                    
                    if (args.length == 1) {
                        VanishToggleMenu menu = new VanishToggleMenu(player);
                        menu.openPlayer();
                    }
                    
                }
            }
        }.setBaseRank(Rank.MODERATOR).addRank(Rank.VIP).addAlias("v");
        
        plugin.getCommandManager().addCommand(vanish);
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!e.getInventory().getTitle().toLowerCase().contains(VanishToggleMenu.getName().toLowerCase())) {
            e.setCancelled(checkCancel(e.getWhoClicked(), VanishToggle.INTERACT, "interact with inventories"));
        } else {
            e.setCancelled(true);
            if (e.getCurrentItem() == null) return;
            if (e.getCurrentItem().getItemMeta() == null) return;
            if (e.getRawSlot() != e.getSlot()) return;
            if (e.getCurrentItem().getItemMeta().getDisplayName() == null) return;
            if (e.getCurrentItem().getItemMeta().getDisplayName().equals("")) return;
            
            FirecraftPlayer player = plugin.getPlayer(e.getWhoClicked().getUniqueId());
    
            ItemStack item = e.getCurrentItem();
            if (item.getType().equals(Material.LIME_DYE) || item.getType().equals(Material.GRAY_DYE)) {
                VanishToggle toggle = VanishToggle.getToggle(item.getItemMeta().getDisplayName());
                player.getVanishInfo().toggle(toggle);
                VanishToggleMenu.Entry entry = VanishToggleMenu.getItemForValue(toggle, player.getVanishInfo().getSetting(toggle));
                e.getInventory().setItem(entry.getSlot(), entry.getItemStack());
            }
        }
    }
    
    @EventHandler
    public void onItemPickup(EntityPickupItemEvent e) {
        e.setCancelled(checkCancel(e.getEntity(), VanishToggle.PICKUP));
    }
    
    @EventHandler
    public void onItemDrop(EntityDropItemEvent e) {
        e.setCancelled(checkCancel(e.getEntity(), VanishToggle.DROP, "drop items"));
    }
    
    @EventHandler
    public void entityDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player)) e.setCancelled(checkCancel(e.getEntity(), VanishToggle.INTERACT));
        else e.setCancelled(checkCancel(e.getDamager(), VanishToggle.INTERACT, "damage entities"));
    }
    
    @EventHandler
    public void entityDamage(EntityDamageByBlockEvent e) {
        e.setCancelled(checkCancel(e.getEntity(), VanishToggle.INTERACT));
    }
    
    @EventHandler
    public void entityTarget(EntityTargetLivingEntityEvent e) {
        e.setCancelled(checkCancel(e.getTarget(), VanishToggle.ENTITY_TARGET));
    }
    
    @EventHandler
    public void onVehicleDestroy(VehicleDestroyEvent e) {
        e.setCancelled(checkCancel(e.getAttacker(), VanishToggle.DESTROY_VEHICLE, "destroy vehicles"));
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        e.setCancelled(checkCancel(e.getPlayer(), VanishToggle.BREAK, "break blocks"));
    }
    
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        e.setCancelled(checkCancel(e.getPlayer(), VanishToggle.PLACE, "place blocks"));
    }
    
    @EventHandler
    public void onPlayerBucketEmptyEvent(PlayerBucketEmptyEvent e) {
        e.setCancelled(checkCancel(e.getPlayer(), VanishToggle.INTERACT, "use buckets"));
    }
    
    @EventHandler
    public void onPlayerBucketFillEvent(PlayerBucketFillEvent e) {
        e.setCancelled(checkCancel(e.getPlayer(), VanishToggle.INTERACT, "use buckets"));
    }
    
    private boolean checkCancel(Entity entity, VanishToggle toggle) {
        if (entity instanceof Player) {
            FirecraftPlayer player = plugin.getPlayer(entity.getUniqueId());
            if (!player.isVanished()) return false;
            return !player.getVanishInfo().getSetting(toggle);
        } else return false;
    }
    
    private boolean checkCancel(Entity entity, VanishToggle toggle, String message) {
        if (entity instanceof Player) {
            FirecraftPlayer player = plugin.getPlayer(entity.getUniqueId());
            if (!player.isVanished()) return false;
            if (!player.getVanishInfo().getSetting(toggle)) {
                player.sendMessage(Messages.cannotActionVanished(message));
                return true;
            }
        }
        return false;
    }
}