package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.interfaces.ICommandManager;
import net.firecraftmc.shared.classes.model.player.FirecraftPlayer;
import net.firecraftmc.shared.command.FirecraftCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet;

public class CommandManager implements ICommandManager {

    private FirecraftCore plugin;
    private HashSet<FirecraftCommand> commands = new HashSet<>();

    public CommandManager(FirecraftCore plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        for (FirecraftCommand command : commands) {
            if (command.getName().equalsIgnoreCase(cmd.getName())) {
                if (sender instanceof ConsoleCommandSender) {
                    command.executeConsole((ConsoleCommandSender) sender, args);
                } else if (sender instanceof Player) {
                    FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
                    if (command.canUse(player)) {
                        if (command.respectsRecordMode()) {
                            if (player.isRecording()) {
                                player.sendMessage(Messages.recordingNoUse);
                                return true;
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
            if (command.getName().equalsIgnoreCase(cmd)) {
                return command;
            } else if (command.hasAlias(cmd)) {
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
}
