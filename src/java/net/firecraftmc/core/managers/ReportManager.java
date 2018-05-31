package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.FirecraftPlayer;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.classes.enums.Rank;
import net.firecraftmc.shared.classes.model.Report;
import net.firecraftmc.shared.packets.FPacketReport;
import net.firecraftmc.shared.packets.staffchat.FPReportAssignOthers;
import net.firecraftmc.shared.packets.staffchat.FPReportAssignSelf;
import net.firecraftmc.shared.packets.staffchat.FPReportSetOutcome;
import net.firecraftmc.shared.packets.staffchat.FPReportSetStatus;
import net.firecraftmc.shared.paginator.Paginator;
import net.firecraftmc.shared.paginator.PaginatorFactory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.*;

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

            Report report = plugin.getDatabase().saveReport(new Report(player.getUniqueId(), target.getUniqueId(), reason, player.getLocation(), System.currentTimeMillis()));
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
                            Report report = plugin.getDatabase().getReport(set.getInt("id"));
                            reports.add(report);
                        }
                    } catch (Exception e) {
                    }
                } else {
                    if (args[1].equalsIgnoreCase("all")) {
                        ResultSet set = plugin.getDatabase().querySQL("SELECT * FROM `reports`;");
                        try {
                            while (set.next()) {
                                Report report = plugin.getDatabase().getReport(set.getInt("id"));
                                reports.add(report);
                            }
                        } catch (Exception e) {
                        }
                    } else {
                        UUID target = null;
                        UUID reporter = null;
                        Report.Status status = null;
                        Report.Outcome outcome = null;
                        UUID assignee = null;
                        for (String a : args) {
                            if (a.startsWith("t:")) {
                                target = plugin.getPlayerManager().getPlayer(a.replace("t:", "")).getUniqueId();
                            }
                            if (a.startsWith("r:")) {
                                reporter = plugin.getPlayerManager().getPlayer(a.replace("r:", "")).getUniqueId();
                            }
                            if (a.startsWith("s:")) {
                                try {
                                    status = Report.Status.valueOf(a.replace("s:", "").toUpperCase());
                                } catch (Exception e) {
                                    player.sendMessage(prefix + "&cThe status you provided was invalid.");
                                }
                            }
                            if (a.startsWith("o:")) {
                                try {
                                    outcome = Report.Outcome.valueOf(a.replace("o:", "").toUpperCase());
                                } catch (Exception e) {
                                    player.sendMessage(prefix + "&cThe outcome you provided was invalid.");
                                }
                            }
                            if (a.startsWith("a:")) {
                                assignee = plugin.getPlayerManager().getPlayer(a.replace("a:", "")).getUniqueId();
                            }
                        }

                        String sql = "SELECT * from `reports`;";
                        ResultSet set = plugin.getDatabase().querySQL(sql);
                        try {
                            while (set.next()) {
                                Report report = plugin.getDatabase().getReport(set.getInt("id"));
                                if (report == null) continue;
                                if (target != null) if (!report.getTarget().equals(target)) continue;
                                if (reporter != null) if (!report.getReporter().equals(reporter)) continue;
                                if (status != null) if (report.getStatus() != status) continue;
                                if (outcome != null) if (report.getOutcome() != outcome) continue;
                                if (assignee != null) {
                                    if (report.getAssignee() != null) {
                                        if (!report.getAssignee().equals(assignee)) continue;
                                    } else continue;
                                }
                                reports.add(report);
                            }
                        } catch (Exception e) {
                        }
                    }
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
                Report report = getReport(args, 2, player);
                if (report == null) return true;
                player.sendMessage("&eViewing details for the report id &4" + report.getId());
                player.sendMessage("&eReporter: &5" + report.getReporterName());
                player.sendMessage("&eTarget: &d" + report.getTargetName());
                player.sendMessage("&eAssignee: &1" + ((report.getAssignee() != null) ? report.getAssigneeName() : "None"));
                player.sendMessage("&eReason: &3" + report.getReason());
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(report.getDate());
                SimpleDateFormat format = new SimpleDateFormat("MM-dd-yyyy h:mm:ss a, z");
                player.sendMessage("&eDate: &7" + format.format(calendar.getTime()));
                player.sendMessage("&eStatus: " + report.getStatus().getColor() + report.getStatus().toString());
                player.sendMessage("&eOutcome: " + ((report.getOutcome() != null) ? report.getOutcome().getColor() + report.getOutcome().toString() : "None"));
            } else if (Utils.Command.checkCmdAliases(args, 0, "teleport", "tp")) {
                Report report = getReport(args, 2, player);
                if (report == null) return true;
                player.teleport(report.getLocation());
                player.sendMessage(prefix + "&bYou teleported to the location of the report with the id &e" + report.getId());
            } else if (Utils.Command.checkCmdAliases(args, 0, "setstatus", "ss")) {
                Report report = getReport(args, 3, player);
                if (report == null) return true;
                if (report.getAssignee() == null) {
                    if (!player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                        player.sendMessage(prefix + "&cThat report is not assigned to anyone. Assign yourself or someone else.");
                        return true;
                    }
                }

                if (!report.getAssignee().equals(player.getUniqueId())) {
                    if (!player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                        player.sendMessage(prefix + "&cThat report is not assigned to you, so you cannot change anything.");
                        return true;
                    }
                }

                Report.Status status;
                try {
                    status = Report.Status.valueOf(args[2].toUpperCase());
                } catch (Exception e) {
                    player.sendMessage(prefix + "&cThe status you provided is invalid.");
                    return true;
                }
                report.setStatus(status);
                plugin.getDatabase().saveReport(report);
                FPReportSetStatus setStatus = new FPReportSetStatus(plugin.getFirecraftServer(), player.getUniqueId(), report.getId(), report.getStatus());
                plugin.getSocket().sendPacket(setStatus);
            } else if (Utils.Command.checkCmdAliases(args, 0, "setoutcome", "so")) {
                Report report = getReport(args, 3, player);
                if (report == null) return true;

                if (report.getAssignee() == null) {
                    if (!player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                        player.sendMessage(prefix + "&cThat report is not assigned to anyone. Assign yourself or someone else.");
                        return true;
                    }
                }

                if (!report.getAssignee().equals(player.getUniqueId())) {
                    if (!player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                        player.sendMessage(prefix + "&cThat report is not assigned to you, so you cannot change anything.");
                        return true;
                    }
                }

                Report.Outcome outcome;
                try {
                    outcome = Report.Outcome.valueOf(args[2].toUpperCase());
                } catch (Exception e) {
                    player.sendMessage(prefix + "&cThe outcome you provided is invalid.");
                    return true;
                }
                report.setOutcome(outcome);
                plugin.getDatabase().saveReport(report);
                FPReportSetOutcome setOutcome = new FPReportSetOutcome(plugin.getFirecraftServer(), player.getUniqueId(), report.getId(), report.getOutcome());
                plugin.getSocket().sendPacket(setOutcome);
            } else if (Utils.Command.checkCmdAliases(args, 0, "page", "p")) {
                Paginator<Report> paginator = this.paginators.get(player.getUniqueId());
                if (paginator == null) {
                    player.sendMessage(prefix + "&cYou currently do not have a query of reports to display.");
                    return true;
                }
                if (args.length != 2) {
                    player.sendMessage(prefix + Messages.notEnoughArgs);
                    return true;
                }
                int pageNumber;
                try {
                    pageNumber = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage(prefix + "The page number you provided is invalid.");
                    return true;
                }
                paginator.display(player.getPlayer(), pageNumber);
            } else if (Utils.Command.checkCmdAliases(args, 0, "assign", "a")) {
                Report report = getReport(args, 3, player);
                if (report == null) {
                    player.sendMessage(prefix + "&cThe report could not be found with that id.");
                    return true;
                }

                if (report.getAssignee() != null) {
                    if (!report.getAssignee().equals(player.getUniqueId())) {
                        FirecraftPlayer assignee = plugin.getDatabase().getPlayer(plugin.getFirecraftServer(), report.getAssignee());
                        if (!player.getMainRank().isEqualToOrHigher(assignee.getMainRank())) {
                            player.sendMessage(prefix + "&cThat report is not assigned to you, so you cannot change anything.");
                            return true;
                        }
                    }
                }

                if (args[2].equalsIgnoreCase("self")) {
                    if (report.isInvolved(player.getUniqueId())) {
                        if (!player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                            player.sendMessage(prefix + "&cYou cannot self-assign a report that you are involved in.");
                            return true;
                        }
                    }
                    report.setAssignee(player.getUniqueId());
                    FPReportAssignSelf selfAssign = new FPReportAssignSelf(plugin.getFirecraftServer(), player.getUniqueId(), report.getId());
                    plugin.getSocket().sendPacket(selfAssign);
                } else {
                    FirecraftPlayer target = plugin.getPlayerManager().getPlayer(args[2]);
                    if (target == null) {
                        player.sendMessage(prefix + "The player name you provided is not valid.");
                        return true;
                    }
                    if (!target.getMainRank().isEqualToOrHigher(Rank.HELPER)) {
                        player.sendMessage(prefix + "&cOnly staff can be assigned to report.");
                        return true;
                    }
                    if (report.isInvolved(player.getUniqueId())) {
                        if (!player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                            player.sendMessage(prefix + "&cYou cannot assign a report to a staff member involved with the report.");
                            return true;
                        }
                    }

                    report.setAssignee(target.getUniqueId());
                    FPReportAssignOthers assignOthers = new FPReportAssignOthers(plugin.getFirecraftServer(), player.getUniqueId(), report.getId(), target.getName());
                    plugin.getSocket().sendPacket(assignOthers);
                }
                plugin.getDatabase().saveReport(report);
            }
        }

        return true;
    }

    /**
     * A private method to reduce code
     *
     * @param args   The command arguments
     * @param length The length the args should be
     * @param player The player to send messages to (CommandSender
     * @return The report based on the id
     */
    private Report getReport(String[] args, int length, FirecraftPlayer player) {
        if (args.length != length) {
            player.sendMessage(prefix + Messages.notEnoughArgs);
            return null;
        }

        int rId;
        try {
            rId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(prefix + "&cThe number for the report id is invalid.");
            return null;
        }

        Report report = plugin.getDatabase().getReport(rId);
        if (report == null) {
            player.sendMessage(prefix + "&cThe report could not be found with that id.");
            return null;
        }
        return report;
    }
}