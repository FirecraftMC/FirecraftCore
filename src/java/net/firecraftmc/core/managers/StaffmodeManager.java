package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.classes.enums.Rank;
import net.firecraftmc.shared.classes.interfaces.IStaffmodeManager;
import net.firecraftmc.shared.classes.model.player.ActionBar;
import net.firecraftmc.shared.classes.model.player.FirecraftPlayer;
import net.firecraftmc.shared.command.FirecraftCommand;
import net.firecraftmc.shared.packets.staffchat.FPSCStaffmodeToggle;
import org.bukkit.GameMode;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class StaffmodeManager implements IStaffmodeManager {

    private List<UUID> staffmode = new ArrayList<>();

    public StaffmodeManager(FirecraftCore plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    
        plugin.getSocket().addSocketListener(packet -> {
            if (packet instanceof FPSCStaffmodeToggle) {
                FPSCStaffmodeToggle toggle = ((FPSCStaffmodeToggle) packet);
                FirecraftPlayer staffMember = plugin.getPlayerManager().getPlayer(toggle.getPlayer());
                String format = Utils.Chat.formatStaffModeToggle(plugin.getFCServer(), staffMember, toggle.getValue());
                Utils.Chat.sendStaffChatMessage(plugin.getPlayerManager().getPlayers(), staffMember, format);
            }
        });
    
        FirecraftCommand staffModeCommand = new FirecraftCommand("staffmode", "Toggles your staff mode status") {
            public void executePlayer(FirecraftPlayer player, String[] args) {
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
                    FPSCStaffmodeToggle toggle = new FPSCStaffmodeToggle(plugin.getFCServer().getId(), player.getUniqueId(), false);
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
                    FPSCStaffmodeToggle toggle = new FPSCStaffmodeToggle(plugin.getFCServer().getId(), player.getUniqueId(), true);
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
            }
        }.setBaseRank(Rank.HELPER).addAlias("sm");
        plugin.getCommandManager().addCommand(staffModeCommand);
    }
    public boolean inStaffMode(FirecraftPlayer player) {
        return this.staffmode.contains(player.getUniqueId());
    }
}
