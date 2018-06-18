package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.Prefixes;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.classes.enums.Rank;
import net.firecraftmc.shared.classes.model.ActionBar;
import net.firecraftmc.shared.classes.model.player.FirecraftPlayer;
import net.firecraftmc.shared.packets.staffchat.FPSCStaffmodeToggle;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class StaffmodeManager implements Listener, CommandExecutor {

    private FirecraftCore plugin;

    private List<UUID> staffmode = new ArrayList<>();

    public StaffmodeManager(FirecraftCore plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        plugin.getSocket().addSocketListener(packet -> {
            if (packet instanceof FPSCStaffmodeToggle) {
                FPSCStaffmodeToggle toggle = ((FPSCStaffmodeToggle) packet);
                FirecraftPlayer staffMember = plugin.getPlayerManager().getPlayer(toggle.getPlayer());
                String format = Utils.Chat.formatStaffModeToggle(plugin.getFirecraftServer(), staffMember, toggle.getValue());
                Utils.Chat.sendStaffChatMessage(plugin.getPlayerManager().getPlayers(), staffMember, format);
            }
        });
    }

    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Prefixes.STAFFMODE + Messages.onlyPlayers);
            return true;
        }

        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
        if (!player.getMainRank().isEqualToOrHigher(Rank.HELPER)) {
            player.sendMessage(Prefixes.STAFFMODE + Messages.noPermission);
            return true;
        }

        if (staffmode.contains(player.getUniqueId())) {
            staffmode.remove(player.getUniqueId());
            player.setActionBar(null);
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.unVanish();
            for (FirecraftPlayer p : plugin.getPlayerManager().getPlayers()) {
                p.getPlayer().showPlayer(player.getPlayer());
                p.getScoreboard().updateScoreboard(p);
            }
            FPSCStaffmodeToggle toggle = new FPSCStaffmodeToggle(plugin.getFirecraftServer(), player.getUniqueId(), false);
            plugin.getSocket().sendPacket(toggle);
        } else {
            staffmode.add(player.getUniqueId());
            player.setActionBar(new ActionBar(Messages.actionBar_Staffmode));
            player.setAllowFlight(true);
            player.setGameMode(GameMode.SPECTATOR);
            player.vanish();
            player.getVanishInfo().toggleChatInteract();
            for (FirecraftPlayer p : plugin.getPlayerManager().getPlayers()) {
                if (!p.getMainRank().isEqualToOrHigher(player.getMainRank())) {
                    p.getPlayer().hidePlayer(player.getPlayer());
                    p.getScoreboard().updateScoreboard(p);
                }
            }
            FPSCStaffmodeToggle toggle = new FPSCStaffmodeToggle(plugin.getFirecraftServer(), player.getUniqueId(), true);
            plugin.getSocket().sendPacket(toggle);

            new BukkitRunnable() {
                public void run() {
                    player.sendMessage("&bYou have turned on staff mode, this means:");
                    player.sendMessage("&8- &eYou are automatically in spectator gamemode");
                    player.sendMessage("&8- &eVanish has automatically been set. Use /vanish settings to toggle what you can and can't do.");
                    player.sendMessage("&8- &eYou can now use /tp <player> to teleport to other players.");
                    player.sendMessage("&8- &eYou can now use /echest <player> to view other player's echests (view-only)");
                    player.sendMessage("&8- &eYou can now use /invsee <player> to view other player's inventories (view-only)");
                    if (player.getMainRank().isEqualToOrHigher(Rank.MODERATOR)) {
                        player.sendMessage("&8- &eYou can now use the /tphere command.");
                    }
                }
            }.runTaskLater(plugin, 5L);
        }
        return true;
    }


    public boolean inStaffMode(FirecraftPlayer player) {
        return this.staffmode.contains(player.getUniqueId());
    }
}
