package net.firecraftmc.core;

import net.firecraftmc.shared.classes.FirecraftPlayer;
import net.firecraftmc.shared.classes.FirecraftPlugin;
import net.firecraftmc.shared.classes.FirecraftSocket;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.packets.FPacketServerPlayerJoin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collection;
import java.util.HashMap;
import java.util.UUID;

public class FirecraftCore extends FirecraftPlugin implements Listener {

    private HashMap<UUID, FirecraftPlayer> onlineFirecraftPlayers = new HashMap<>();

    private FirecraftSocket socket;
    private String server;

    public void onEnable() {
        this.saveDefaultConfig();
        this.socket = new FirecraftSocket(this, "localhost", 1234);
        this.socket.start();
        this.getServer().getPluginManager().registerEvents(this, this);
        this.server = getConfig().getString("server");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        e.setJoinMessage(null);

        Player player = e.getPlayer();
        player.sendMessage("§7§oPlease wait while we retrieve your player data...");
        socket.sendPacket(new FPacketServerPlayerJoin(server, player.getUniqueId()));
        new BukkitRunnable() {
            public void run() {
                if (onlineFirecraftPlayers.get(player.getUniqueId()) != null) {
                    player.sendMessage("§7§oYour data has been successfully retrieved!");
                    //TODO Make this a bit more rank based in the future
                    for (FirecraftPlayer fp : onlineFirecraftPlayers.values()) {
                        fp.sendMessage("&e" + player.getName() + " joined the game.");
                    }
                    this.cancel();
                }
            }
        }.runTaskTimerAsynchronously(this, 5L, 1L);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        if (getFirecraftPlayer(player.getUniqueId()) == null) {
            player.sendMessage("§c§oYour playerdata has not been recieved yet, you cannot speak.");
            e.setCancelled(true);
            return;
        }

        //TODO Make this channel based
        e.setFormat(Utils.formatChat(getFirecraftPlayer(player.getUniqueId()), e.getMessage()));
    }

    public void addFirecraftPlayer(FirecraftPlayer firecraftPlayer) {
        firecraftPlayer.setPlayer(Bukkit.getPlayer(firecraftPlayer.getUuid()));
        this.onlineFirecraftPlayers.put(firecraftPlayer.getUuid(), firecraftPlayer);
    }

    public FirecraftPlayer getFirecraftPlayer(UUID uuid) {
        return onlineFirecraftPlayers.get(uuid);
    }

    public Collection<FirecraftPlayer> getFirecraftPlayers() {
        return onlineFirecraftPlayers.values();
    }
}