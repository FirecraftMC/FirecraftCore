package net.firecraftmc.core.managers;

import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.interfaces.IToggleManager;
import net.firecraftmc.api.menus.PlayerToggleMenu;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.toggles.Toggle;
import net.firecraftmc.core.FirecraftCore;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class ToggleManager implements IToggleManager {
    
    private FirecraftCore plugin;
    
    public ToggleManager(FirecraftCore plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        FirecraftCommand toggles = new FirecraftCommand("toggles", "Show the Toggles menu") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                PlayerToggleMenu menu = new PlayerToggleMenu(player);
                menu.openPlayer();
            }
        }.setBaseRank(Rank.DEFAULT).setRespectsRecordMode(false);
        
        plugin.getCommandManager().addCommand(toggles);
    }
    
    @EventHandler
    public void onInventoryInteract(InventoryClickEvent e) {
        if (e.getInventory().getTitle().contains(PlayerToggleMenu.getName())) {
            if (e.getRawSlot() != e.getSlot()) return;
            e.setCancelled(true);
            if (e.getCurrentItem() == null) return;
            if (e.getCurrentItem().getItemMeta() == null) return;
            if (e.getCurrentItem().getItemMeta().getDisplayName() == null) return;
            if (e.getCurrentItem().getItemMeta().getDisplayName().equals("")) return;
            
            FirecraftPlayer player = plugin.getPlayer(e.getWhoClicked().getUniqueId());
            
            ItemStack item = e.getCurrentItem();
            if (item.getType().equals(Material.LIME_DYE) || item.getType().equals(Material.GRAY_DYE)) {
                Toggle toggle = Toggle.getToggle(item.getItemMeta().getDisplayName());
                player.toggle(toggle);
                PlayerToggleMenu.Entry entry = PlayerToggleMenu.getItemForValue(toggle, player.getToggleValue(toggle));
                e.getInventory().setItem(entry.getSlot(), entry.getItemStack());
                toggle.onToggle(player.getToggleValue(toggle), player);
            }
        }
    }
}