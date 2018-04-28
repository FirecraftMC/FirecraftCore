package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.FirecraftPlayer;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.enums.Rank;
import org.apache.commons.lang.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ItemManager implements TabExecutor {
    
    private FirecraftCore plugin;
    public ItemManager(FirecraftCore plugin) {
        this.plugin = plugin;
    }
    
    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (cmd.getName().equalsIgnoreCase("setname")) {
            if (sender instanceof Player) {
                Player p = (Player) sender;
                FirecraftPlayer player = plugin.getPlayerManager().getPlayer(p.getUniqueId());
                if (player.getMainRank().isEqualToOrHigher(Rank.MAJOR)) {
                    if (p.getInventory().getItemInMainHand() != null && p.getInventory().getItemInMainHand().getType() != Material.AIR) {
                        if (args.length == 0) {
                            player.sendMessage("&cUsage: /setname [name].");
                            return false;
                        }
                        String newName = Utils.color(StringUtils.join(args, " ", 0, args.length));
                    
                        ItemStack newItem = new ItemStack(p.getInventory().getItemInMainHand().getType(), p.getInventory().getItemInMainHand().getAmount());
                        ItemMeta newItemMeta = newItem.getItemMeta();
                        newItemMeta.setDisplayName(newName);
                        newItemMeta.setLore(p.getInventory().getItemInMainHand().getItemMeta().getLore());
                        newItem.setDurability(p.getInventory().getItemInMainHand().getDurability());
                        newItem.addEnchantments(p.getInventory().getItemInMainHand().getEnchantments());
                        newItem.setItemMeta(newItemMeta);
                    
                        p.getInventory().setItem(p.getInventory().getHeldItemSlot(), newItem);
                    } else {
                        player.sendMessage("&cYou must have an item in your hand.");
                    }
                } else {
                    player.sendMessage("&cInsufficient Permission.");
                }
            } else {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cYou must be a player to use this command."));
            }
        }
    
        if (cmd.getName().equalsIgnoreCase("setlore")) {
            if (sender instanceof Player) {
                Player p = (Player) sender;
                FirecraftPlayer player = plugin.getPlayerManager().getPlayer(p.getUniqueId());
                if (player.getMainRank().isEqualToOrHigher(Rank.MAJOR)) {
                    if (p.getInventory().getItemInMainHand() != null && p.getInventory().getItemInMainHand().getType() != Material.AIR) {
                        if (args.length == 0) {
                            player.sendMessage("&cUsage: /setlore [lore].");
                            return false;
                        }
                        String newLore = Utils.color(StringUtils.join(args, " ", 0, args.length));
                    
                        ItemStack newItem = new ItemStack(p.getInventory().getItemInMainHand().getType(), p.getInventory().getItemInMainHand().getAmount());
                        ItemMeta newItemMeta = newItem.getItemMeta();
                        newItemMeta.setDisplayName(p.getInventory().getItemInMainHand().getItemMeta().getDisplayName());
                        List<String> lore = new ArrayList<>();
                        lore.add(newLore);
                        newItemMeta.setLore(lore);
                        newItem.setDurability(p.getInventory().getItemInMainHand().getDurability());
                        newItem.addEnchantments(p.getInventory().getItemInMainHand().getEnchantments());
                        newItem.setItemMeta(newItemMeta);
                    
                        p.getInventory().setItem(p.getInventory().getHeldItemSlot(), newItem);
                    } else {
                        player.sendMessage("&cYou must have an item in your hand.");
                    }
                } else {
                    player.sendMessage("&cInsufficient Permission.");
                }
            } else {
                sender.sendMessage(Utils.color("&cYou must be a player to use this command."));
            }
        }
    
        return true;
    }
    
    public List<String> onTabComplete(CommandSender sender, Command cmd, String s, String[] args) {
        return null;
    }
}