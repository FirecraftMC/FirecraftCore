package net.firecraftmc.core.managers;

import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.util.Messages;
import net.firecraftmc.core.FirecraftCore;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class InventoryManager implements Listener {
    
    public InventoryManager(FirecraftCore plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    
        FirecraftCommand clearInventory = new FirecraftCommand("clearinventory", "Clears your inventory or another player's") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (args.length == 1) {
                    if (player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                        FirecraftPlayer target = plugin.getPlayerManager().getPlayer(args[0]);
                        if (!target.getMainRank().isEqualToOrHigher(player.getMainRank())) {
                            target.getInventory().clear();
                            target.sendMessage("<nc>Your inventory was cleared by <vc>" + player.getName());
                            player.sendMessage("<nc>You cleared <vc>" + target.getName() + "'s <nc>inventory.");
                        } else {
                            player.sendMessage("<ec>You can only clear the inventory of players that are lower in rank.");
                        }
                    } else {
                        player.sendMessage("<ec>Only Admins or higher can clear other player's inventories.");
                    }
                } else {
                    player.getInventory().clear();
                    player.sendMessage("<nc>You cleared your inventory.");
                }
            }
        };
        clearInventory.addAlias("ci").setBaseRank(Rank.PHOENIX);
        
        FirecraftCommand enderChest = new FirecraftCommand("enderchest", "Open your ender chest or that of another player.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (args.length == 1) {
                    if (!player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                        if (!plugin.getStaffmodeManager().inStaffMode(player)) {
                            player.sendMessage("<ec>You do not have permission to open the ender chests of other players.");
                            return;
                        }
                    }
                    
                    FirecraftPlayer target = plugin.getPlayerManager().getPlayer(args[0]);
                    if (target.getMainRank().isEqualToOrHigher(player.getMainRank())) {
                        if (!Rank.bothFT(player, target)) {
                            player.sendMessage("<ec>You can only view the ender chests of players that are of lower rank.");
                            return;
                        }
                    }
                    
                    Inventory inv = Bukkit.createInventory(null, 27, target.getName() + "'s Enderchest");
                    for (int i = 0; i < 27; i++) {
                        ItemStack stack = target.getPlayer().getEnderChest().getItem(i);
                        if (stack != null) {
                            inv.setItem(i, stack);
                        }
                    }
                    player.getPlayer().openInventory(inv);
                    player.sendMessage("<nc>You opened <vc>" + target.getName() + "'s <nc>enderchest (view-only)");
                } else {
                    player.getPlayer().openInventory(player.getPlayer().getEnderChest());
                    player.sendMessage("<nc>Here is your enderchest.");
                }
            }
        };
        enderChest.addAlias("ec").setBaseRank(Rank.PHOENIX);
        
        FirecraftCommand workbench = new FirecraftCommand("workbench", "Opens a portable workbench.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                Inventory workbench = Bukkit.createInventory(null, InventoryType.WORKBENCH, "Workbench");
                player.getPlayer().openInventory(workbench);
                player.sendMessage("<nc>Here is a workbench");
            }
        };
        workbench.addAliases("wb", "craft").setBaseRank(Rank.EMBER);
        
        FirecraftCommand invsee = new FirecraftCommand("invsee", "View the inventory of another player.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (!(args.length > 0)) {
                    player.sendMessage(Messages.notEnoughArgs);
                    return;
                }
    
                if (!player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                    if (!plugin.getStaffmodeManager().inStaffMode(player)) {
                        player.sendMessage("<vc>You do not have permission to view the inventories of other players.");
                        return;
                    }
                }
                FirecraftPlayer target = plugin.getPlayerManager().getPlayer(args[0]);
                if (target.getPlayer() == null) {
                    player.sendMessage("<ec>Sorry, but viewing offline player's inventories is not yet supported.");
                    return;
                }
    
                if (target.getMainRank().isEqualToOrHigher(player.getMainRank())) {
                    if (!Rank.bothFT(target, player)) {
                        player.sendMessage("<ec>You cannot open the inventory of a player with the same rank or higher than yours.");
                        return;
                    }
                }

                Inventory inventory = Bukkit.createInventory(null, 45, target.getName() + "'s Inventory");
                for (int i = 0; i < 36; i++) {
                    ItemStack item = target.getInventory().getItem(i);
                    if (item != null) {
                        inventory.setItem(i, item);
                    }
                }
                if (target.getInventory().getHelmet() != null) {
                    inventory.setItem(36, target.getInventory().getHelmet());
                }
                if (target.getInventory().getChestplate() != null) {
                    inventory.setItem(37, target.getInventory().getChestplate());
                }
                if (target.getInventory().getLeggings() != null) {
                    inventory.setItem(38, target.getInventory().getLeggings());
                }
                if (target.getInventory().getBoots() != null) {
                    inventory.setItem(39, target.getInventory().getBoots());
                }
                try {
                    if (target.getInventory().getItemInOffHand() != null) {
                        inventory.setItem(44, target.getInventory().getItemInOffHand());
                    }
                } catch (Exception e) {
                }
                player.getPlayer().openInventory(inventory);
                player.sendMessage("<nc>Here is <vc>" + target.getName() + "'s <nc>inventory (view-only)");
            }
        };
        invsee.setBaseRank(Rank.MOD);
        
        plugin.getCommandManager().addCommands(clearInventory, enderChest, workbench, invsee);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getClickedInventory() != null) {
            if (e.getCursor() != null) {
                if (e.getClickedInventory().getTitle() != null) {
                    if (e.getClickedInventory().getTitle().contains("'s Inventory") || e.getClickedInventory().getTitle().contains("'s Enderchest")) {
                        e.setCancelled(true);
                    }
                }
            }
        }
    }
}
