package net.firecraftmc.core;

import net.firecraftmc.shared.classes.FirecraftPlayer;
import net.firecraftmc.shared.classes.FirecraftPlugin;
import net.firecraftmc.shared.classes.FirecraftSocket;
import net.firecraftmc.shared.packets.FPacketServerPlayerJoin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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
        this.socket = new FirecraftSocket(this, "localhost", 1234);
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
                    player.sendMessage("§7§lSuccessfully retrieved your data!");
                    //TODO Make this a bit more rank based in the future
                    getFirecraftPlayers().forEach(firecraftPlayer -> firecraftPlayer.sendMessage("&e" + e.getPlayer().getName() + " joined the game."));
                    this.cancel();
                }
            }
        }.runTaskTimerAsynchronously(this, 5L, 1L);
    }

    public void addFirecraftPlayer(FirecraftPlayer firecraftPlayer) {
        this.onlineFirecraftPlayers.put(firecraftPlayer.getUuid(), firecraftPlayer);
    }

    public FirecraftPlayer getFirecraftPlayer(UUID uuid) {
        return onlineFirecraftPlayers.get(uuid);
    }

    public Collection<FirecraftPlayer> getFirecraftPlayers() {
        return onlineFirecraftPlayers.values();
    }
}