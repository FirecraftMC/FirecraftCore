package net.firecraftmc.core.managers;

import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.packets.staffchat.FPSCIncognitoToggle;
import net.firecraftmc.core.FirecraftCore;
import org.bukkit.event.Listener;

public class IncognitoManager implements Listener {
    
    private final FirecraftCore plugin;
    
    public IncognitoManager(FirecraftCore plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    
        
        //TODO Implement staff chat stuff
        plugin.getSocket().addSocketListener(packet -> {
            if (packet instanceof FPSCIncognitoToggle) {
                FPSCIncognitoToggle toggleVanish = ((FPSCIncognitoToggle) packet);
                FirecraftPlayer staffMember = plugin.getPlayerManager().getPlayer(toggleVanish.getPlayer());
            }
        });
        
        
    }
    
}
