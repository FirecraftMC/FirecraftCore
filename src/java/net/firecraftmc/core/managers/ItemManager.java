package net.firecraftmc.core.managers;

import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.util.Messages;
import net.firecraftmc.api.util.Utils;
import net.firecraftmc.core.FirecraftCore;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ItemManager {
    
    public ItemManager(FirecraftCore plugin) {
        FirecraftCommand setname = new FirecraftCommand("setname", "Set the display name of an item.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (player.getInventory().getItemInMainHand() != null && player.getInventory().getItemInMainHand().getType() != Material.AIR) {
                    if (args.length == 0) {
                        player.sendMessage(Messages.notEnoughArgs);
                        return;
                    }
                    String newName = Utils.color(StringUtils.join(args, " ", 0, args.length));
                    
                    ItemStack item = player.getInventory().getItemInHand();
                    ItemMeta itemMeta = item.getItemMeta();
                    itemMeta.setDisplayName(newName);
                    item.setItemMeta(itemMeta);
                    
                    player.getInventory().setItem(player.getInventory().getHeldItemSlot(), item);
                } else {
                    player.sendMessage(Messages.noItemInHand);
                }
            }
        };
        setname.setBaseRank(Rank.FIRECRAFT_TEAM);
        
        FirecraftCommand setlore = new FirecraftCommand("setlore", "Sets the lore of an item.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (player.getInventory().getItemInMainHand() != null && player.getInventory().getItemInMainHand().getType() != Material.AIR) {
                    if (args.length == 0) {
                        player.sendMessage(Messages.notEnoughArgs);
                        return;
                    }
                    String newLore = Utils.color(StringUtils.join(args, " ", 0, args.length));
        
                    ItemStack item = player.getInventory().getItemInHand();
                    ItemMeta itemMeta = item.getItemMeta();
                    List<String> lore = new ArrayList<>();
                    lore.add(newLore);
                    itemMeta.setLore(lore);
                    item.setItemMeta(itemMeta);
    
                    player.getInventory().setItem(player.getInventory().getHeldItemSlot(), item);
                } else {
                    player.sendMessage(Messages.noItemInHand);
                }
            }
        };
        setlore.setBaseRank(Rank.FIRECRAFT_TEAM);
        
        plugin.getCommandManager().addCommands(setname, setlore);
    }
}