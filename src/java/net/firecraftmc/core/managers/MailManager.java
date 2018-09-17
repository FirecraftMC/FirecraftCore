package net.firecraftmc.core.managers;

import net.firecraftmc.api.command.FirecraftCommand;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.model.player.Mail;
import net.firecraftmc.api.packets.FPacketMail;
import net.firecraftmc.api.toggles.Toggle;
import net.firecraftmc.core.FirecraftCore;
import org.apache.commons.lang.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Calendar;

public class MailManager {
    
    public MailManager(FirecraftCore plugin) {
        plugin.getSocket().addSocketListener(packet -> {
            if (packet instanceof FPacketMail) {
                FPacketMail mailPacket = ((FPacketMail) packet);
                Mail mail = plugin.getFCDatabase().getMail(mailPacket.getId());
                String senderName = plugin.getFCDatabase().getPlayerName(mail.getSender());
                FirecraftPlayer receiver = plugin.getPlayer(mail.getReceiver());
                if (receiver.getPlayer() != null) {
                    receiver.sendMessage("<nc>You have received mail from <vc>" + senderName);
                    receiver.sendMessage("<nc>Use the command <vc>/mail read " + mail.getId() + " <nc>to read the message.");
                }
            }
        });
        
        FirecraftCommand mail = new FirecraftCommand("mail", "Main command for the mail feature") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (!(args.length > 0)) {
                    player.sendMessage("<ec>Usage: /mail <send|read|list> [arguments]");
                    return;
                }
                
                this.executeSubCommand(args[0], player, args);
            }
        }.setBaseRank(Rank.DEFAULT);
        
        FirecraftCommand mailSend = new FirecraftCommand("send", "Send a mail message to a player.") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (!(args.length > 2)) {
                    player.sendMessage("<ec>Usage: /mail send <player> <message>");
                    return;
                }
                
                FirecraftPlayer target = plugin.getPlayer(args[1]);
                if (!target.getProfile().getToggleValue(Toggle.getToggle("messages"))) {
                    if (!Rank.isStaff(player.getMainRank())) {
                        player.sendMessage("<ec>That player has messages disabled");
                        return;
                    } else {
                        if (!player.getMainRank().isEqualToOrHigher(target.getMainRank())) {
                            player.sendMessage("<ec>That player has messages disabled");
                            return;
                        }
                    }
                }
                long date = System.currentTimeMillis();
                String text = StringUtils.join(args, " ", 2, args.length);
                Mail mail = plugin.getFCDatabase().createMail(date, player.getUniqueId(), target.getUniqueId(), text, false);
                if (mail == null) {
                    player.sendMessage("<ec>There was an error creating that mail message.");
                    return;
                }
                FPacketMail packetMail = new FPacketMail(plugin.getFCServer().getId(), mail.getId());
                plugin.getSocket().sendPacket(packetMail);
                player.sendMessage("<nc>You sent a mail message to <vc>" + target.getName() + " <nc>with the message <vc>" + text);
            }
        };
        
        FirecraftCommand mailRead = new FirecraftCommand("read", "Read mail messages") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                if (args.length != 2) {
                    player.sendMessage("<ec>You must supply a mail message id.");
                    return;
                }
                
                if (args[1].equalsIgnoreCase("all")) {
                    for (Mail mail : plugin.getFCDatabase().getMailByReceiver(player.getUniqueId())) {
                        plugin.getFCDatabase().setMailRead(mail.getId());
                    }
                    player.sendMessage("<nc>You have marked all mail messages as read.");
                    return;
                }
                
                Mail mail = plugin.getFCDatabase().getMail(Integer.parseInt(args[1]));
                if (mail == null) {
                    player.sendMessage("<ec>The mail message id you supplied is invalid.");
                    return;
                }
                
                String sender = plugin.getFCDatabase().getPlayerName(mail.getSender());
                
                player.sendMessage("");
                player.sendMessage("<nc>You have received this message from <vc>" + sender);
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(mail.getDate());
                SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy h:mm a, z");
                player.sendMessage("<nc>This message was sent on <vc>" + dateFormat.format(calendar.getTime()));
                player.sendMessage("<vc>" + mail.getText());
                player.sendMessage("");
                plugin.getFCDatabase().setMailRead(mail.getId());
            }
        };
        
        FirecraftCommand mailList = new FirecraftCommand("list", "List unread mail messages") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
                player.sendMessage("<ec>This command is not supported yet.");
            }
        };
        
        mail.addSubcommand(mailSend).addSubcommand(mailRead).addSubcommand(mailList);
        
        plugin.getCommandManager().addCommand(mail);
    }
}