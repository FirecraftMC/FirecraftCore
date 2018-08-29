package net.firecraftmc.core.managers;

import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.interfaces.ICommandManager;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.toggles.Toggle;
import net.firecraftmc.api.util.Messages;
import net.firecraftmc.core.FirecraftCore;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.HashSet;
import java.util.Set;

public class CommandManager implements ICommandManager {
    
    private final FirecraftCore plugin;
    private final Set<FirecraftCommand> commands = new HashSet<>();
    
    public CommandManager(FirecraftCore plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCmdPreProcess(PlayerCommandPreprocessEvent e) {
        plugin.getFCDatabase().saveCommand(e.getPlayer().getUniqueId(), plugin.getFCServer(), System.currentTimeMillis(), e.getMessage());
    }
    
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        for (FirecraftCommand command : commands) {
            if (command.getName().equalsIgnoreCase(cmd.getName())) {
                if (sender instanceof ConsoleCommandSender) {
                    sender.sendMessage(Messages.onlyPlayers);
                } else if (sender instanceof Player) {
                    FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
                    if (command.canUse(player)) {
                        if (command.respectsRecordMode()) {
                            if (player.getToggleValue(Toggle.RECORDING)) {
                                player.sendMessage(Messages.recordingNoUse);
                                continue;
                            }
                        }
                        command.executePlayer(player, args);
                    } else {
                        player.sendMessage(Messages.noPermission);
                    }
                }
            }
        }
        
        return true;
    }
    
    public FirecraftCommand getCommand(String cmd) {
        for (FirecraftCommand command : commands) {
            if (command.getName().equalsIgnoreCase(cmd) || command.hasAlias(cmd)) {
                return command;
            }
        }
        return null;
    }
    
    public void addCommand(FirecraftCommand command) {
        this.commands.add(command);
    }
    
    public void addCommands(FirecraftCommand... cmds) {
        for (FirecraftCommand cmd : cmds) {
            addCommand(cmd);
        }
    }
    
    public void removeCommand(String name) {
        this.commands.remove(getCommand(name));
    }
    
    public void removeCommand(FirecraftCommand command) {
        this.commands.remove(command);
    }
    
    public Set<FirecraftCommand> getCommands() {
        return commands;
    }
}
