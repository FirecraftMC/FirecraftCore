package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.FirecraftPlayer;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.classes.enums.Rank;
import net.firecraftmc.shared.classes.model.Report;
import net.firecraftmc.shared.packets.FPacketReport;
import net.firecraftmc.shared.paginator.Paginator;
import net.firecraftmc.shared.paginator.PaginatorFactory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

            if (!(args.length > 0)) {
                player.sendMessage(prefix + Messages.noPermission);
                return true;
            }

            if (Utils.Command.checkCmdAliases(args, 0, "list", "l")) {
                List<Report> reports = new ArrayList<>();
                if (!(args.length > 1)) {
                    ResultSet set = plugin.getDatabase().querySQL("SELECT * FROM `reports` WHERE `status` <> 'CLOSED';");
                    try {
                        while (set.next()) {
                            Report report = Utils.Database.getReportFromDatabase(plugin.getDatabase(), set.getInt("id"));
                            reports.add(report);
                        }
                    } catch (Exception e) {}
                }

                PaginatorFactory<Report> paginatorFactory = new PaginatorFactory<>();
                paginatorFactory.setMaxElements(10).setHeader("§aReports page {pagenumber} out of {totalpages}").setFooter("§aUse /reportadmin page {nextpage} to view the next page.");
                reports.forEach(report -> paginatorFactory.addElement(report, reports.size()));
                if (paginatorFactory.getPages().isEmpty()) {
                    player.sendMessage(prefix + "&cThere are no reports to display.");
                    return true;
                } else {
                    Paginator<Report> paginator = paginatorFactory.build();
                    paginators.put(player.getUniqueId(), paginator);
                    paginator.display(player.getPlayer(), 1);
                }
            } else if (Utils.Command.checkCmdAliases(args, 0, "view", "v")) {

            } else if (Utils.Command.checkCmdAliases(args, 0, "teleport", "tp")) {

            } else if (Utils.Command.checkCmdAliases(args, 0, "setstatus", "ss")) {

            } else if (Utils.Command.checkCmdAliases(args, 0, "setoutcome", "so")) {

            } else if (Utils.Command.checkCmdAliases(args, 0, "page", "p")) {

            } else if (Utils.Command.checkCmdAliases(args, 0, "refresh", "r")) {

            } else if (Utils.Command.checkCmdAliases(args, 0, "assign", "a")) {

            } else if (Utils.Command.checkCmdAliases(args, 0, "help", "h")) {

            }
        }

        return true;
    }
}