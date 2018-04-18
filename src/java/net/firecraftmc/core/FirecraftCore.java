package net.firecraftmc.core;

import net.firecraftmc.core.managers.*;
import net.firecraftmc.core.wrapper.NickWrapper1_12_R1;
import net.firecraftmc.core.wrapper.NickWrapper1_8_R3;
import net.firecraftmc.shared.classes.*;
import net.firecraftmc.shared.classes.utils.ReflectionUtils;
import net.firecraftmc.shared.packets.FPacketServerDisconnect;
import org.bukkit.ChatColor;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;

public class FirecraftCore extends FirecraftPlugin implements Listener {
    
    private PlayerManager playerManager;
    private NickWrapper nickWrapper;
    
    private FirecraftSocket socket;
    private FirecraftServer server;
    
    public void onEnable() {
        instance = this;
        this.saveDefaultConfig();
        if (getConfig().contains("host")) {
            getConfig().set("host", "172.18.0.2");
            saveConfig();
        }
        
        String host = getConfig().getString("host");
        this.socket = new FirecraftSocket(this, host, getConfig().getInt("port"));
        this.socket.start();
        this.getServer().getPluginManager().registerEvents(this, this);
        this.server = new FirecraftServer(getConfig().getString("server.name"), ChatColor.valueOf(getConfig().getString("server.color")));
        
        String versionString = ReflectionUtils.getVersion();
        if (versionString.equalsIgnoreCase("v1_8_R3")) {
            this.nickWrapper = new NickWrapper1_8_R3();
        } else if (versionString.equalsIgnoreCase("v1_12_R1")) {
            this.nickWrapper = new NickWrapper1_12_R1();
        }
        
        new BukkitRunnable() {
            public void run() {
                if (socket == null || !socket.isOpen()) {
                    socket = new FirecraftSocket(instance, "127.0.0.1", getConfig().getInt("port"));
                }
            }
        }.runTaskTimerAsynchronously(this, 5 * 60L, 60L);
        
        this.playerManager = new PlayerManager(this);
        
        this.getCommand("chat").setExecutor(new ChatManager(this));
        NickManager nickManager = new NickManager(this);
        this.getCommand("nick").setExecutor(nickManager);
        this.getCommand("nickcancel").setExecutor(nickManager);
        this.getCommand("nickconfirm").setExecutor(nickManager);
        this.getCommand("nickreset").setExecutor(nickManager);
        
        GamemodeManager gmManager = new GamemodeManager(this);
        this.getCommand("gamemode").setExecutor(gmManager);
        this.getCommand("gmc").setExecutor(gmManager);
        this.getCommand("gms").setExecutor(gmManager);
        this.getCommand("gma").setExecutor(gmManager);
        this.getCommand("gmsp").setExecutor(gmManager);
        
        TeleportationManager tpManager = new TeleportationManager(this);
        this.getCommand("teleport").setExecutor(tpManager);
        this.getCommand("tphere").setExecutor(tpManager);
        this.getCommand("back").setExecutor(tpManager);
        this.getCommand("tpall").setExecutor(tpManager);
        this.getCommand("tpaccept").setExecutor(tpManager);
        this.getCommand("tpdeny").setExecutor(tpManager);
        this.getCommand("tpa").setExecutor(tpManager);
        
        this.getCommand("vanish").setExecutor(new VanishManager(this));
        this.getCommand("viewprofile").setExecutor(playerManager);
    }
    
    public void onDisable() {
        if (socket != null) {
            socket.sendPacket(new FPacketServerDisconnect(server));
            socket.close();
        }
    }
    
    public NickWrapper getNickWrapper() {
        return nickWrapper;
    }
    
    public FirecraftSocket getSocket() {
        return socket;
    }
    
    public IPlayerManager getPlayerManager() {
        return playerManager;
    }
    
    public FirecraftServer getFirecraftServer() {
        return server;
    }
}