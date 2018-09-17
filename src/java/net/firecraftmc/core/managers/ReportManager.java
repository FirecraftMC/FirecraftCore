package net.firecraftmc.core.managers;

import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.model.Report;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.packets.FPacketReport;
import net.firecraftmc.api.packets.staffchat.*;
import net.firecraftmc.api.paginator.Paginator;
import net.firecraftmc.api.paginator.PaginatorFactory;
import net.firecraftmc.api.toggles.Toggle;
import net.firecraftmc.api.util.*;
import net.firecraftmc.core.FirecraftCore;

import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.*;

public class ReportManager {
    
    private final FirecraftCore plugin;
    
    private final HashMap<UUID, Paginator<Report>> paginators = new HashMap<>();
    
    public ReportManager(FirecraftCore plugin) {
        this.plugin = plugin;
        
        plugin.getSocket().addSocketListener(packet -> {
            if (packet instanceof FPacketReport) {
                Utils.Socket.handleReport(packet, plugin.getFCServer(), plugin.getFCDatabase(), plugin.getPlayerManager().getPlayers());
            } else if (packet instanceof FPReportAssignOthers) {
                FPReportAssignOthers assignOthers = ((FPReportAssignOthers) packet);
                FirecraftPlayer staffMember = plugin.getPlayerManager().getPlayer(assignOthers.getPlayer());
                String format = Utils.Chat.formatReportAssignOthers(plugin.getFCServer().getName(), staffMember.getName(), assignOthers.getAssignee(), assignOthers.getId());
                plugin.getPlayerManager().getPlayers().forEach(p -> {
                    if (Rank.isStaff(p.getMainRank())) {
                        if (!p.getToggleValue(Toggle.RECORDING)) {
                            p.sendMessage(format);
                        }
                    }
                });
            } else if (packet instanceof FPReportAssignSelf) {
                FPReportAssignSelf assignSelf = ((FPReportAssignSelf) packet);
                FirecraftPlayer staffMember = plugin.getPlayerManager().getPlayer(assignSelf.getPlayer());
                String format = Utils.Chat.formatReportAssignSelf(plugin.getFCServer().getName(), staffMember.getName(), assignSelf.getId());
                plugin.getPlayerManager().getPlayers().forEach(p -> {
                    if (Rank.isStaff(p.getMainRank())) {
                        if (!p.getToggleValue(Toggle.RECORDING)) {
                            p.sendMessage(format);
                        }
                    }
                });
            } else if (packet instanceof FPReportSetOutcome) {
                FPReportSetOutcome setOutcome = ((FPReportSetOutcome) packet);
                FirecraftPlayer staffMember = plugin.getPlayerManager().getPlayer(setOutcome.getPlayer());
                String format = Utils.Chat.formatReportSetOutcome(plugin.getFCServer().getName(), staffMember.getName(), setOutcome.getId(), setOutcome.getOutcome());
                plugin.getPlayerManager().getPlayers().forEach(p -> {
                    if (Rank.isStaff(p.getMainRank())) {
                        if (!p.getToggleValue(Toggle.RECORDING)) {
                            p.sendMessage(format);
                        }
                    }
                });
            } else if (packet instanceof FPReportSetStatus) {
                FPReportSetStatus setOutcome = ((FPReportSetStatus) packet);
                FirecraftPlayer staffMember = plugin.getPlayerManager().getPlayer(setOutcome.getPlayer());
                String format = Utils.Chat.formatReportSetStatus(plugin.getFCServer().getName(), staffMember.getName(), setOutcome.getId(), setOutcome.getStatus());
                plugin.getPlayerManager().getPlayers().forEach(p -> {
                    if (Rank.isStaff(p.getMainRank())) {
                        if (!p.getToggleValue(Toggle.RECORDING)) {
                            p.sendMessage(format);
                        }
                    }
                });
            }
        });
        
        FirecraftCommand report = new FirecraftCommand("report", "Report another player.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                FirecraftPlayer target;
                UUID uuid = null;
                try {
                    uuid = UUID.fromString(args[0]);
                } catch (Exception e) {
                }
                
                target = uuid != null ? plugin.getPlayerManager().getPlayer(uuid) : plugin.getPlayerManager().getPlayer(args[0]);
                
                if (target == null) {
                    player.sendMessage(Prefixes.REPORT + "<ec>That name or uuid is not valid.");
                    return;
                }
                
                String reason = Utils.getReason(1, args);
                
                Report report = plugin.getFCDatabase().saveReport(new Report(player.getUniqueId(), target.getUniqueId(), reason, player.getLocation(), System.currentTimeMillis()));
                if (report.getId() == 0) {
                    player.sendMessage(Prefixes.REPORT + "<ec>There was an unknown error with the database, report not filed.");
                    return;
                }
                FPacketReport packetReport = new FPacketReport(plugin.getFCServer().getId(), report.getId());
                plugin.getSocket().sendPacket(packetReport);
                player.sendMessage(Prefixes.REPORT + "<nc>Your report has been successfully filed for staff review.");
            }
        };
        report.setBaseRank(Rank.DEFAULT);
        
        FirecraftCommand reportAdmin = new FirecraftCommand("reportadmin", "Manage reports") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (!(args.length > 0)) {
                    player.sendMessage(Prefixes.REPORT + "<ec>You do not have enough arguments.");
                    return;
                }
                
                if (Utils.Command.checkCmdAliases(args, 0, "list", "l")) {
                    List<Report> reports = new ArrayList<>();
                    if (!(args.length > 1)) {
                        ResultSet set = plugin.getFCDatabase().querySQL("SELECT * FROM `reports` WHERE `status` <> 'CLOSED';");
                        try {
                            while (set.next()) {
                                Report report = plugin.getFCDatabase().getReport(set.getInt("id"));
                                reports.add(report);
                            }
                        } catch (Exception e) {
                        }
                    } else {
                        if (args[1].equalsIgnoreCase("all")) {
                            ResultSet set = plugin.getFCDatabase().querySQL("SELECT * FROM `reports`;");
                            try {
                                while (set.next()) {
                                    Report report = plugin.getFCDatabase().getReport(set.getInt("id"));
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
                                        player.sendMessage(Prefixes.REPORT + Messages.reportInvalidValue("status"));
                                    }
                                }
                                if (a.startsWith("o:")) {
                                    try {
                                        outcome = Report.Outcome.valueOf(a.replace("o:", "").toUpperCase());
                                    } catch (Exception e) {
                                        player.sendMessage(Prefixes.REPORT + Messages.reportInvalidValue("outcome"));
                                    }
                                }
                                if (a.startsWith("a:")) {
                                    assignee = plugin.getPlayerManager().getPlayer(a.replace("a:", "")).getUniqueId();
                                }
                            }
                            
                            String sql = "SELECT * from `reports`;";
                            ResultSet set = plugin.getFCDatabase().querySQL(sql);
                            try {
                                while (set.next()) {
                                    Report report = plugin.getFCDatabase().getReport(set.getInt("id"));
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
                    paginatorFactory.setMaxElements(7).setHeader("§aReports page {pagenumber} out of {totalpages}").setFooter("§aUse /reportadmin page {nextpage} to view the next page.");
                    reports.forEach(paginatorFactory::addElement);
                    Paginator<Report> paginator = paginatorFactory.build();
                    paginators.put(player.getUniqueId(), paginator);
                    paginator.display(player.getPlayer(), 1);
                } else if (Utils.Command.checkCmdAliases(args, 0, "view", "v")) {
                    Report report = getReport(args, 2, player);
                    if (report == null) return;
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
                    if (report == null) return;
                    player.teleport(report.getLocation());
                    player.sendMessage(Prefixes.REPORT + Messages.reportTeleport(report.getId()));
                } else if (Utils.Command.checkCmdAliases(args, 0, "setstatus", "ss")) {
                    Report report = getReport(args, 3, player);
                    if (report == null) return;
                    if (report.getAssignee() == null) {
                        if (!player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                            player.sendMessage(Prefixes.REPORT + "&cThat report is not assigned to anyone. Assign yourself or someone else.");
                            return;
                        }
                    }
                    
                    Report.Status status;
                    try {
                        status = Report.Status.valueOf(args[2].toUpperCase());
                    } catch (Exception e) {
                        player.sendMessage(Prefixes.REPORT + Messages.reportInvalidValue("status"));
                        return;
                    }
                    
                    if (!report.getStatus().equals(Report.Status.PENDING)) {
                        if (!report.getAssignee().equals(player.getUniqueId())) {
                            if (!player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                                player.sendMessage(Prefixes.REPORT + "&cThat report is not assigned to you, so you cannot change anything.");
                                return;
                            }
                        }
                    }
                    report.setStatus(status);
                    plugin.getFCDatabase().saveReport(report);
                    FPReportSetStatus setStatus = new FPReportSetStatus(plugin.getFCServer().getId(), player.getUniqueId(), report.getId(), report.getStatus());
                    plugin.getSocket().sendPacket(setStatus);
                    addReportChange(report, "changed status " + status.toString());
                } else if (Utils.Command.checkCmdAliases(args, 0, "setoutcome", "so")) {
                    Report report = getReport(args, 3, player);
                    if (report == null) return;
                    
                    if (report.getAssignee() == null) {
                        if (!player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                            player.sendMessage(Prefixes.REPORT + "&cThat report is not assigned to anyone. Assign yourself or someone else.");
                            return;
                        }
                    }
                    
                    if (!report.getAssignee().equals(player.getUniqueId())) {
                        if (!player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                            player.sendMessage(Prefixes.REPORT + "&cThat report is not assigned to you, so you cannot change anything.");
                            return;
                        }
                    }
                    
                    Report.Outcome outcome;
                    try {
                        outcome = Report.Outcome.valueOf(args[2].toUpperCase());
                    } catch (Exception e) {
                        player.sendMessage(Prefixes.REPORT + Messages.reportInvalidValue("outcome"));
                        return;
                    }
                    report.setOutcome(outcome);
                    plugin.getFCDatabase().saveReport(report);
                    FPReportSetOutcome setOutcome = new FPReportSetOutcome(plugin.getFCServer().getId(), player.getUniqueId(), report.getId(), report.getOutcome());
                    plugin.getSocket().sendPacket(setOutcome);
                    addReportChange(report, "changed outcome " + outcome.toString());
                } else if (Utils.Command.checkCmdAliases(args, 0, "page", "p")) {
                    Paginator<Report> paginator = paginators.get(player.getUniqueId());
                    if (paginator == null) {
                        player.sendMessage(Prefixes.REPORT + "&cYou currently do not have a query of reports to display.");
                        return;
                    }
                    if (args.length != 2) {
                        player.sendMessage(Prefixes.REPORT + Messages.notEnoughArgs);
                        return;
                    }
                    int pageNumber;
                    try {
                        pageNumber = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        player.sendMessage(Prefixes.REPORT + "&cThe page number you provided is invalid.");
                        return;
                    }
                    paginator.display(player.getPlayer(), pageNumber);
                } else if (Utils.Command.checkCmdAliases(args, 0, "assign", "a")) {
                    Report report = getReport(args, 3, player);
                    if (report == null) {
                        player.sendMessage(Prefixes.REPORT + "&cThe report could not be found with that id.");
                        return;
                    }
                    
                    if (report.getAssignee() != null) {
                        if (!report.getAssignee().equals(player.getUniqueId())) {
                            FirecraftPlayer assignee = plugin.getFCDatabase().getPlayer(report.getAssignee());
                            if (!player.getMainRank().isEqualToOrHigher(assignee.getMainRank())) {
                                player.sendMessage(Prefixes.REPORT + "&cThat report is not assigned to you, so you cannot change anything.");
                                return;
                            }
                        }
                    }
                    
                    if (args[2].equalsIgnoreCase("self")) {
                        if (report.isInvolved(player.getUniqueId())) {
                            if (!player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                                player.sendMessage(Prefixes.REPORT + "&cYou cannot self-assign a report that you are involved in.");
                                return;
                            }
                        }
                        report.setAssignee(player.getUniqueId());
                        FPReportAssignSelf selfAssign = new FPReportAssignSelf(plugin.getFCServer().getId(), player.getUniqueId(), report.getId());
                        plugin.getSocket().sendPacket(selfAssign);
                        addReportChange(report, "assigned " + player.getName());
                    } else {
                        FirecraftPlayer target = plugin.getPlayerManager().getPlayer(args[2]);
                        if (target == null) {
                            player.sendMessage(Prefixes.REPORT + "&cThe player name you provided is not valid.");
                            return;
                        }
                        if (!target.getMainRank().isEqualToOrHigher(Rank.TRIAL_MOD)) {
                            player.sendMessage(Prefixes.REPORT + "&cOnly staff can be assigned to report.");
                            return;
                        }
                        if (report.isInvolved(player.getUniqueId())) {
                            if (!player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                                player.sendMessage(Prefixes.REPORT + "&cYou cannot assign a report to a staff member involved with the report.");
                                return;
                            }
                        }
                        
                        report.setAssignee(target.getUniqueId());
                        if (target.getUniqueId().equals(player.getUniqueId())) {
                            FPReportAssignSelf selfAssign = new FPReportAssignSelf(plugin.getFCServer().getId(), player.getUniqueId(), report.getId());
                            plugin.getSocket().sendPacket(selfAssign);
                        } else {
                            FPReportAssignOthers assignOthers = new FPReportAssignOthers(plugin.getFCServer().getId(), player.getUniqueId(), report.getId(), target.getName());
                            plugin.getSocket().sendPacket(assignOthers);
                        }
                        addReportChange(report, "assigned " + target.getName());
                    }
                    plugin.getFCDatabase().saveReport(report);
                }
            }
        };
        reportAdmin.setBaseRank(Rank.TRIAL_MOD).addAlias("ra");
        
        plugin.getCommandManager().addCommands(report, reportAdmin);
    }
    
    private Report getReport(String[] args, int length, FirecraftPlayer player) {
        if (args.length != length) {
            player.sendMessage(Prefixes.REPORT + Messages.notEnoughArgs);
            return null;
        }
        
        int rId;
        try {
            rId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(Prefixes.REPORT + "<ec>The number for the report id is invalid.");
            return null;
        }
        
        Report report = plugin.getFCDatabase().getReport(rId);
        if (report == null) {
            player.sendMessage(Prefixes.REPORT + "<ec>The report could not be found with that id.");
            return null;
        }
        return report;
    }
    
    private void addReportChange(Report report, String change) {
        FirecraftPlayer reporter = plugin.getPlayerManager().getPlayer(report.getReporter());
        int id = plugin.getFCDatabase().addReportChange(report.getId(), change);
        reporter.addUnseenReportAction(id);
        if (reporter.getPlayer() != null) {
            reporter.sendMessage(Messages.formatReportChange(report, change));
            reporter.removeUnseenReportAction(id);
        } else {
            plugin.getFCDatabase().savePlayer(reporter);
            System.out.println(reporter.getUnseenReportActions());
        }
    }
}