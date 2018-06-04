package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.model.FirecraftPlayer;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.enums.Rank;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class InventoryManager implements CommandExecutor, Listener {
    private FirecraftCore plugin;

    public InventoryManager(FirecraftCore plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
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

    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Messages.onlyPlayers);
            return true;
        }

        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());

        if (cmd.getName().equalsIgnoreCase("clearinventory")) {
            if (args.length > 0) {
                if (player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                    FirecraftPlayer target = plugin.getPlayerManager().getPlayer(args[0]);
                    if (target.getMainRank().isEqualToOrHigher(player.getMainRank())) {
                        player.sendMessage(Messages.noClearInvHigher);
                        return true;
                    }

                    target.getInventory().clear();
                    player.sendMessage(Messages.clearInventoryOthers(target.getName()));
                    target.sendMessage(Messages.clearInventoryTarget(player.getName()));
                } else {
                    player.sendMessage(Messages.noPermission);
                    return true;
                }
            } else {
                if (player.getMainRank().isEqualToOrHigher(Rank.CORPORAL)) {
                    player.getPlayer().getInventory().clear();
                    player.sendMessage(Messages.clearInventory);
                } else {
                    player.sendMessage(Messages.noPermission);
                    return true;
                }
            }
        } else if (cmd.getName().equalsIgnoreCase("enderchest")) {
            if (player.getMainRank().isEqualToOrHigher(Rank.SERGEANT)) {
                if (args.length > 0) {
                    if (player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                        FirecraftPlayer target = plugin.getPlayerManager().getPlayer(args[0]);
                        if (!target.isOnline()) {
                            player.sendMessage(Messages.offlineNotSupported);
                            return true;
                        }

                        if (target.getMainRank().isEqualToOrHigher(player.getMainRank())) {
                            player.sendMessage(Messages.noEchestOpenHigher);
                            return true;
                        } else {
                            Inventory inventory = Bukkit.createInventory(null, 27, target.getName() + "'s Enderchest");
                            for (int i = 0; i <27; i++) {
                                ItemStack stack = target.getPlayer().getEnderChest().getItem(i);
                                if (stack != null) {
                                    inventory.setItem(i, stack);
                                }
                            }
                            player.getPlayer().openInventory(inventory);
                            player.sendMessage(Messages.enderChestOthers(target.getName()));
                            return true;
                        }
                    }
                } else {
                    player.getPlayer().openInventory(player.getPlayer().getEnderChest());
                    player.sendMessage(Messages.enderchestSelf);
                    return true;
                }
            } else {
                player.sendMessage(Messages.noPermission);
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("workbench")) {
            if (player.getMainRank().isEqualToOrHigher(Rank.CAPTAIN)) {
                Inventory workbench = Bukkit.createInventory(null, InventoryType.WORKBENCH, "Workbench");
                player.getPlayer().openInventory(workbench);
                player.sendMessage(Messages.workbench);
            } else {
                player.sendMessage(Messages.noPermission);
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("invsee")) {
            if (!(args.length > 0)) {
                player.sendMessage(Messages.notEnoughArgs);
                return true;
            }

            if (player.getMainRank().isEqualToOrHigher(Rank.HELPER)) {
                FirecraftPlayer target = plugin.getPlayerManager().getPlayer(args[0]);
                if (target.getPlayer() == null) {
                    player.sendMessage(Messages.offlineNotSupported);
                    return true;
                }

                if (target.getMainRank().equals(player.getMainRank())) {
                    player.sendMessage(Messages.noInvOpenHigher);
                    return true;
                }

                /*
                00 01 02 03 04 05 06 07 08
                09 10 11 12 13 14 15 16 17
                18 19 20 21 22 23 24 25 26
                27 28 29 30 31 32 33 34 35
                h c l b - - - - O
                 */

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
                } catch (Exception e) {}
                player.getPlayer().openInventory(inventory);
                player.sendMessage(Messages.invSeeViewOnly(target.getName()));
            } else {
                player.sendMessage(Messages.noPermission);
                return true;
            }
        }

        return true;
    }
}
