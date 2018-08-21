package net.firecraftmc.core.managers;

import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.enums.Toggle;
import net.firecraftmc.api.interfaces.IToggleManager;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.util.ItemStackBuilder;
import net.firecraftmc.core.FirecraftCore;
import org.bukkit.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class ToggleManager implements IToggleManager {
    
    public ToggleManager(FirecraftCore plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        FirecraftCommand toggles = new FirecraftCommand("toggles", "Show the Toggles menu") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                player.getPlayer().openInventory(generateToggleInventory(player));
                player.sendMessage("&7You have opened the Toggles Menu. (Currently View Only)");
            }
        }.setBaseRank(Rank.DEFAULT);
        
        plugin.getCommandManager().addCommand(toggles);
    }
    
    @EventHandler
    public void onInventoryInteract(InventoryClickEvent e) {
        if (e.getInventory().getName().contains("'s Toggles")) {
            e.setCancelled(true);
            
            ItemStack item = e.getCurrentItem();
            if (item != null) {
                if (item.getItemMeta() != null) {
                    if (item.getItemMeta().getDisplayName() != null) {
                        if (item.getType().equals(Material.LIME_DYE) || item.getType().equals(Material.GRAY_DYE)) {
                            Toggle toggle;
                            try {
                                Toggle.valueOf(item.getItemMeta().getDisplayName().replace(" ", "_"));
                            } catch (Exception ex) { return; }
                            
                            
                        }
                    }
                }
            }
        }
    }
    
    private Inventory generateToggleInventory(FirecraftPlayer player) {
        Inventory inventory = Bukkit.createInventory(null, 54, player.getName() + "'s Toggles");
        Map<Integer, ItemStack> toggleStacks = new HashMap<>();
        for (Entry<Toggle, Boolean> entry : player.getProfile().getToggles().entrySet()) {
            Toggle toggle = entry.getKey();
            ItemStackBuilder toggleItemBuilder = new ItemStackBuilder(toggle.getMaterial())
                    .withLore("&7Description coming soon.")
                    .withName(ChatColor.GOLD + toggle.name().replace("_", " "));
            ItemStackBuilder valueItemBuilder;
            if (entry.getValue()) {
                valueItemBuilder = new ItemStackBuilder(Material.LIME_DYE)
                        .withName(ChatColor.GREEN + toggle.name().replace("_", " "))
                        .withLore("&7Click to disable");
            } else {
                valueItemBuilder = new ItemStackBuilder(Material.GRAY_DYE)
                        .withName(ChatColor.RED + toggle.name().replace("_", " "))
                        .withLore("&7Click to enable");
            }
            
            toggleStacks.put(toggle.getSlot(), toggleItemBuilder.buildItem());
            toggleStacks.put(toggle.getSlot() + 9, valueItemBuilder.buildItem());
        }
        
        for (Entry<Integer, ItemStack> entry : toggleStacks.entrySet()) {
            inventory.setItem(entry.getKey(), entry.getValue());
        }
        
        return inventory;
    }
}