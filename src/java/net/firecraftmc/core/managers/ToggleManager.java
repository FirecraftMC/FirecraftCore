package net.firecraftmc.core.managers;

import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.interfaces.IToggleManager;
import net.firecraftmc.api.menus.VanishToggleMenu;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.toggles.Toggle;
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
    
    private FirecraftCore plugin;
    
    private ItemStack enabledItem, disabledItem, toggleDisabled;
    
    public ToggleManager(FirecraftCore plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        FirecraftCommand toggles = new FirecraftCommand("toggles", "Show the Toggles menu") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                player.getPlayer().openInventory(generateToggleInventory(player));
                player.sendMessage("&7You have opened the Toggles Menu.");
            }
        }.setBaseRank(Rank.DEFAULT).setRespectsRecordMode(false);
        
        plugin.getCommandManager().addCommand(toggles);
        
        enabledItem = new ItemStackBuilder(Material.LIME_DYE).withName(ChatColor.GREEN + "{toggle}").withLore("&7Click to disable").buildItem();
        disabledItem = new ItemStackBuilder(Material.GRAY_DYE).withName(ChatColor.RED + "{toggle}").withLore("&7Click to enable").buildItem();
        toggleDisabled = new ItemStackBuilder(Material.BARRIER).withName(ChatColor.RED + "That toggle is currently disabled.").buildItem();
    }
    
    @EventHandler
    public void onInventoryInteract(InventoryClickEvent e) {
        if (e.getInventory().getName().contains("'s Toggles")) {
            e.setCancelled(true);
            
            ItemStack item = e.getCurrentItem();
            if (item != null) {
                if (item.getItemMeta() != null) {
                    if (item.getItemMeta().getDisplayName() != null) {
                        FirecraftPlayer player = plugin.getPlayer(e.getWhoClicked().getUniqueId());
                        if (item.getType().equals(Material.LIME_DYE) || item.getType().equals(Material.GRAY_DYE)) {
                            Toggle toggle;
                            try {
                                toggle = Toggle.getToggle(ChatColor.stripColor(item.getItemMeta().getDisplayName()));
                            } catch (Exception ex) {
                                System.out.println(ex.getMessage());
                                return;
                            }
                            
                            if (!toggle.isEnabled()) {
                                e.getInventory().setItem(e.getSlot(), toggleDisabled);
                                player.getPlayer().updateInventory();
                                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                    try {
                                        e.getInventory().setItem(e.getSlot(), item);
                                        player.getPlayer().updateInventory();
                                    } catch (Exception ex) {
                                    }
                                }, 3 * 20);
                            } else {
                                boolean value = !player.getProfile().getToggleValue(toggle);
                                ItemStackBuilder valueItemBuilder;
                                if (value) {
                                    valueItemBuilder = new ItemStackBuilder(enabledItem).withName(enabledItem.getItemMeta().getDisplayName().replace("{toggle}", toggle.getName())).withLore("&7Click to disable");
                                } else {
                                    valueItemBuilder = new ItemStackBuilder(disabledItem).withName(disabledItem.getItemMeta().getDisplayName().replace("{toggle}", toggle.getName())).withLore("&7Click to enable");
                                }
                                e.getClickedInventory().setItem(e.getSlot(), valueItemBuilder.buildItem());
                                player.getPlayer().updateInventory();
                                player.getProfile().toggle(toggle);
                                toggle.onToggle(value, player);
                            }
                        } else if (item.getType().equals(Toggle.VANISH.getMaterial())) {
                            if (player.isVanished()) {
                                e.setCancelled(true);
                                e.setCurrentItem(null);
                                Bukkit.getScheduler().runTaskLater(plugin, () -> new VanishToggleMenu(player).openPlayer(), 2L);
                            } else {
                                player.sendMessage("<ec>You cannot open the Vanish Settings menu if you are not vanished.");
                            }
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
            ItemStackBuilder toggleItemBuilder = new ItemStackBuilder(toggle.getMaterial()).withLore("&7" + toggle.getDescription()).withName(ChatColor.GOLD + toggle.getName());
            ItemStackBuilder valueItemBuilder;
            if (entry.getValue()) {
                valueItemBuilder = new ItemStackBuilder(enabledItem).withName(enabledItem.getItemMeta().getDisplayName().replace("{toggle}", toggle.getName())).withLore("&7Click to disable");
            } else {
                valueItemBuilder = new ItemStackBuilder(disabledItem).withName(disabledItem.getItemMeta().getDisplayName().replace("{toggle}", toggle.getName())).withLore("&7Click to enable");
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