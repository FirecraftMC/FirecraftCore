package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.FirecraftPlayer;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.classes.enums.Rank;
import net.firecraftmc.shared.classes.model.Report;
import net.firecraftmc.shared.packets.FPacketReport;
import net.firecraftmc.shared.paginator.Paginator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.UUID;

/**
 * The Class for managing the report command.
 */
public class ReportManager implements CommandExecutor {

    private FirecraftCore plugin;
    private final String prefix = "&d&l[Report] ";

    private HashMap<UUID, Paginator<Report>> paginators = new HashMap<>();

    public ReportManager(FirecraftCore plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Messages.onlyPlayers);
            return true;
        }

        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());

        if (cmd.getName().equalsIgnoreCase("report")) {
            if (!(args.length > 1)) {
                player.sendMessage(prefix + Messages.notEnoughArgs);
                return true;
            }
            FirecraftPlayer target;
            UUID uuid = null;
            try {
                uuid = UUID.fromString(args[0]);
            } catch (Exception e) {
            }

            if (uuid != null) {
                target = plugin.getPlayerManager().getPlayer(uuid);
            } else {
                target = plugin.getPlayerManager().getPlayer(args[0]);
            }

            if (target == null) {
                player.sendMessage(prefix + Messages.reportInvalidTarget);
                return true;
            }

            String reason = Utils.getReason(1, args);

            Report report = Utils.Database.saveReportToDatabase(plugin.getDatabase(), new Report(player.getUniqueId(), target.getUniqueId(), reason, player.getLocation(), System.currentTimeMillis()));
            if (report.getId() == 0) {
                player.sendMessage(prefix + Messages.reportDatabaseError);
                return true;
            }
            FPacketReport packetReport = new FPacketReport(plugin.getFirecraftServer(), report.getId());
            plugin.getSocket().sendPacket(packetReport);
            player.sendMessage(prefix + Messages.successfulReportFile);
        } else if (cmd.getName().equalsIgnoreCase("reportadmin")) {
            if (!player.getMainRank().isEqualToOrHigher(Rank.HELPER)) {
                player.sendMessage(prefix + Messages.noPermission);
                return true;
            }

            if (args.length > 0) {
                player.sendMessage(prefix + Messages.noPermission);
                return true;
            }

            if (Utils.Command.checkCmdAliases(args, 0, "list", "l")) {
            /*
            /reportadmin list|l (STATUS|ALL) (OUTCOME|ALL)
            In order to provide an outcome, you need a status or provide all
            Status and outcome arguments are optional and are not case sensitive
             */

            } else if (Utils.Command.checkCmdAliases(args, 0, "view", "v")) {

            } else if (Utils.Command.checkCmdAliases(args, 0, "teleport", "tp")) {

            } else if (Utils.Command.checkCmdAliases(args, 0, "setstatus", "ss")) {

            } else if (Utils.Command.checkCmdAliases(args, 0, "setoutcome", "so")) {

            } else if (Utils.Command.checkCmdAliases(args, 0, "page", "p")) {

            } else if (Utils.Command.checkCmdAliases(args, 0, "refresh", "r")) {

            } else if (Utils.Command.checkCmdAliases(args, 0, "help", "h")) {

            }
        }

        return true;
    }
}