package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.model.FirecraftPlayer;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.classes.enums.Rank;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ItemManager implements CommandExecutor {
    
    private FirecraftCore plugin;
    public ItemManager(FirecraftCore plugin) {
        this.plugin = plugin;
    }
    
    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (cmd.getName().equalsIgnoreCase("setname")) {
            if (sender instanceof Player) {
                Player p = (Player) sender;
                FirecraftPlayer player = plugin.getPlayerManager().getPlayer(p.getUniqueId());
                if (player.getMainRank().isEqualToOrHigher(Rank.INFERNO)) {
                    if (p.getInventory().getItemInMainHand() != null && p.getInventory().getItemInMainHand().getType() != Material.AIR) {
                        if (args.length == 0) {
                            player.sendMessage(Messages.notEnoughArgs);
                            return true;
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
                        player.sendMessage(Messages.noItemInHand);
                    }
                } else {
                    player.sendMessage(Messages.noPermission);
                }
            } else {
                sender.sendMessage(Messages.onlyPlayers);
            }
        }
    
        if (cmd.getName().equalsIgnoreCase("setlore")) {
            if (sender instanceof Player) {
                Player p = (Player) sender;
                FirecraftPlayer player = plugin.getPlayerManager().getPlayer(p.getUniqueId());
                if (player.getMainRank().isEqualToOrHigher(Rank.INFERNO)) {
                    if (p.getInventory().getItemInMainHand() != null && p.getInventory().getItemInMainHand().getType() != Material.AIR) {
                        if (args.length == 0) {
                            player.sendMessage(Messages.notEnoughArgs);
                            return true;
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
                        player.sendMessage(Messages.noItemInHand);
                    }
                } else {
                    player.sendMessage(Messages.noPermission);
                }
            } else {
                sender.sendMessage(Messages.onlyPlayers);
            }
        }
    
        return true;
    }
}