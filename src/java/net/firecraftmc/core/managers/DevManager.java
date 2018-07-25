package net.firecraftmc.core.managers;

import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.util.Messages;
import net.firecraftmc.api.util.Utils;
import net.firecraftmc.core.FirecraftCore;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.LeavesDecayEvent;

import java.io.File;

public class DevManager implements Listener {
    
    private boolean decay = true;
    
    public DevManager(FirecraftCore plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        if (plugin.getConfig().contains("decay")) {
            this.decay = plugin.getConfig().getBoolean("decay");
        } else {
            plugin.getConfig().set("decay", decay);
            plugin.saveConfig();
        }

        FirecraftCommand dev = new FirecraftCommand("dev", "Developer only commands and settings.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (args.length > 0) {
                    for (FirecraftCommand cmd : subCommands) {
                        if (cmd.getName().equalsIgnoreCase(args[0]) || cmd.hasAlias(args[0].toLowerCase())) {
                            if (cmd.canUse(player)) {
                                cmd.executePlayer(player, args);
                            } else {
                                player.sendMessage(Messages.noPermission);
                            }
                        }
                    }
                } else {
                    player.sendMessage("<ec>You must provide a sub command.");
                }
            }
        }.setBaseRank(Rank.FIRECRAFT_TEAM);

        FirecraftCommand testMsgSub = new FirecraftCommand("testmsg", "Command that displays the message given with no formatting.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                StringBuilder msg = new StringBuilder();
                for (int i=1; i<args.length; i++) {
                    msg.append(args[i]).append(" ");
                }
                player.sendMessage(msg.toString());
            }
        };
        testMsgSub.addAlias("tm");

        FirecraftCommand decaySub = new FirecraftCommand("decay", "Toggles the decaying of leaves for this server.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                decay = !decay;
                plugin.getConfig().set("decay", decay);
                plugin.saveConfig();
                plugin.reloadConfig();
                player.sendMessage("<nc>You have toggled decaying of leaves to <vc>" + decay);
            }
        };
        
        FirecraftCommand clearLogs = new FirecraftCommand("clearlogs", "Clears the Minecraft Logs in the /logs folder") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                File logDir = new File(plugin.getServer().getWorldContainer() + File.separator + "logs");
                Utils.purgeDirectory(logDir);
                player.sendMessage("<nc>You cleared all of the Minecraft logs");
            }
        }.addAlias("cl");
        
        dev.addSubcommand(testMsgSub);
        dev.addSubcommand(decaySub);
        dev.addSubcommand(clearLogs);
        plugin.getCommandManager().addCommand(dev);
    }

    @EventHandler
    public void onLeafDecay(LeavesDecayEvent e) {
        if (!decay) e.setCancelled(true);
    }
}