package net.firecraftmc.core.managers;

import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.menus.PlayerToggleMenu;
import net.firecraftmc.api.menus.VanishToggleMenu;
import net.firecraftmc.api.model.player.ActionBar;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.packets.staffchat.FPSCVanishToggle;
import net.firecraftmc.api.util.*;
import net.firecraftmc.api.vanish.VanishSetting;
import net.firecraftmc.core.FirecraftCore;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleDamageEvent;
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
                        boolean flight = player.getVanishSettings().allowFlightBeforeVanish();
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
                        player.getVanishSettings().setAllowFlightBeforeVanish(player.getPlayer().getAllowFlight());
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
                        PlayerToggleMenu menu = new PlayerToggleMenu(player);
                        menu.openPlayer();
                    } else {
                        player.sendMessage(Prefixes.VANISH + "<ec>Command based toggling is currently disabled");
                    }
                }
            }
        }.setBaseRank(Rank.TRIAL_MOD).addRank(Rank.BUILD_TEAM).addRank(Rank.VIP).addAlias("v");
        
        plugin.getCommandManager().addCommand(vanish);
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent e) {
        String title = e.getInventory().getTitle().toLowerCase();
        if (title.contains(VanishToggleMenu.getName().toLowerCase())) {
            if (e.getRawSlot() != e.getSlot()) return;
            e.setCancelled(true);
            if (e.getCurrentItem() == null) return;
            if (e.getCurrentItem().getItemMeta() == null) return;
            if (e.getCurrentItem().getItemMeta().getDisplayName() == null) return;
            if (e.getCurrentItem().getItemMeta().getDisplayName().equals("")) return;
            e.setCancelled(true);
            
            FirecraftPlayer player = plugin.getPlayer(e.getWhoClicked().getUniqueId());
            
            ItemStack item = e.getCurrentItem();
            if (item.getType().equals(Material.LIME_DYE) || item.getType().equals(Material.GRAY_DYE)) {
                VanishSetting toggle = VanishSetting.getToggle(item.getItemMeta().getDisplayName());
                player.getVanishSettings().toggle(toggle);
                if (toggle.equals(VanishSetting.COLLISION)) {
                    if (player.isVanished())
                        player.getPlayer().setCollidable(!player.getVanishSettings().getSetting(toggle));
                }
                VanishToggleMenu.Entry entry = VanishToggleMenu.getItemForValue(toggle, player.getVanishSettings().getSetting(toggle));
                e.getInventory().setItem(entry.getSlot(), entry.getItemStack());
            }
        } else {
            if (!title.contains(PlayerToggleMenu.getName().toLowerCase())) {
                e.setCancelled(checkCancel(e.getWhoClicked(), VanishSetting.INTERACT, "interact with inventories"));
            }
        }
    }
    
    @EventHandler
    public void onItemPickup(EntityPickupItemEvent e) {
        e.setCancelled(checkCancel(e.getEntity(), VanishSetting.PICKUP));
    }
    
    @EventHandler
    public void onItemDrop(PlayerDropItemEvent e) {
        e.setCancelled(checkCancel(e.getPlayer(), VanishSetting.DROP, "drop items"));
    }
    
    @EventHandler
    public void entityDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player)) e.setCancelled(checkCancel(e.getEntity(), VanishSetting.DAMAGE));
        else e.setCancelled(checkCancel(e.getDamager(), VanishSetting.DAMAGE, "damage entities"));
    }
    
    @EventHandler
    public void entityDamage(EntityDamageByBlockEvent e) {
        e.setCancelled(checkCancel(e.getEntity(), VanishSetting.DAMAGE));
    }
    
    @EventHandler
    public void entityTarget(EntityTargetLivingEntityEvent e) {
        e.setCancelled(checkCancel(e.getTarget(), VanishSetting.ENTITY_TARGET));
    }
    
    @EventHandler
    public void onVehicleDamage(VehicleDamageEvent e) {
        e.setCancelled(checkCancel(e.getAttacker(), VanishSetting.DESTROY_VEHICLE, "damage vehicles"));
    }
    
    @EventHandler
    public void onVehicleDestroy(VehicleDestroyEvent e) {
        e.setCancelled(checkCancel(e.getAttacker(), VanishSetting.DESTROY_VEHICLE, "destroy vehicles"));
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        e.setCancelled(checkCancel(e.getPlayer(), VanishSetting.BREAK, "break blocks"));
    }
    
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        e.setCancelled(checkCancel(e.getPlayer(), VanishSetting.PLACE, "place blocks"));
    }
    
    @EventHandler
    public void onPlayerBucketEmptyEvent(PlayerBucketEmptyEvent e) {
        e.setCancelled(checkCancel(e.getPlayer(), VanishSetting.INTERACT, "use buckets"));
    }
    
    @EventHandler
    public void onPlayerBucketFillEvent(PlayerBucketFillEvent e) {
        e.setCancelled(checkCancel(e.getPlayer(), VanishSetting.INTERACT, "use buckets"));
    }
    
    private boolean checkCancel(Entity entity, VanishSetting toggle) {
        if (entity instanceof Player) {
            FirecraftPlayer player = plugin.getPlayer(entity.getUniqueId());
            if (!player.isVanished()) return false;
            return !player.getVanishSettings().getSetting(toggle);
        } else return false;
    }
    
    private boolean checkCancel(Entity entity, VanishSetting toggle, String message) {
        if (entity instanceof Player) {
            FirecraftPlayer player = plugin.getPlayer(entity.getUniqueId());
            if (!player.isVanished()) return false;
            if (!player.getVanishSettings().getSetting(toggle)) {
                player.sendMessage(Messages.cannotActionVanished(message));
                return true;
            }
        }
        return false;
    }
}