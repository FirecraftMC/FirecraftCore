package net.firecraftmc.core.managers;

import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.packets.staffchat.FPSCIncognitoToggle;
import net.firecraftmc.api.toggles.Toggle;
import net.firecraftmc.api.util.Utils;
import net.firecraftmc.core.FirecraftCore;
import org.bukkit.event.Listener;

public class IncognitoManager implements Listener {
    
    public IncognitoManager(FirecraftCore plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getSocket().addSocketListener(packet -> {
            if (packet instanceof FPSCIncognitoToggle) {
                FPSCIncognitoToggle toggleIncognito = ((FPSCIncognitoToggle) packet);
                FirecraftPlayer staffMember = plugin.getPlayerManager().getPlayer(toggleIncognito.getPlayer());
                String format = Utils.Chat.formatIncognitoToggle(plugin.getServerManager().getServer(toggleIncognito.getServerId()), staffMember, staffMember.getToggleValue(Toggle.INCOGNITO));
                Utils.Chat.sendStaffChatMessage(plugin.getPlayerManager().getPlayers(), staffMember, format);
            }
        });
    
        FirecraftCommand incognito = new FirecraftCommand("incognito", "Toggle the ability to be seen by other players.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
        
            }
        }.setBaseRank(Rank.TRIAL_MOD).addAlias("ig");
        
        plugin.getCommandManager().addCommand(incognito);
    }
    
}
