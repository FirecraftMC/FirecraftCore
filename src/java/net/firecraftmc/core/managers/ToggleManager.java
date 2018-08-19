package net.firecraftmc.core.managers;

import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.interfaces.IToggleManager;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.core.FirecraftCore;

public class ToggleManager implements IToggleManager {
    
    public ToggleManager(FirecraftCore plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        FirecraftCommand toggles = new FirecraftCommand("toggles", "Show the Toggles menu") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
            
            }
        };
    }
}