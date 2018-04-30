package net.firecraftmc.core;

import net.firecraftmc.core.managers.*;
import net.firecraftmc.core.wrapper.NickWrapper1_12_R1;
import net.firecraftmc.core.wrapper.NickWrapper1_8_R3;
import net.firecraftmc.shared.MySQL;
import net.firecraftmc.shared.classes.*;
import net.firecraftmc.shared.packets.FPacketServerDisconnect;
import org.bukkit.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.ResultSet;

public class FirecraftCore extends FirecraftPlugin implements Listener {
    private PlayerManager playerManager;
    private NickWrapper nickWrapper;
    private FirecraftSocket socket;
    private FirecraftServer server;
    private Location serverSpawn;
    private Location jailLocation;
    private MySQL database;
    
    public void onEnable() {
        instance = this;
        this.saveDefaultConfig();
        if (!getConfig().contains("host")) {
            getConfig().set("host", "172.18.0.2");
            saveConfig();
        }
    
        this.playerManager = new PlayerManager(this);
        
        String host = getConfig().getString("host");
        this.socket = new FirecraftSocket(this, host, getConfig().getInt("port"));
        this.socket.start();
        this.getServer().getPluginManager().registerEvents(this, this);
        this.server = new FirecraftServer(getConfig().getString("server.name"), ChatColor.valueOf(getConfig().getString("server.color")));
    
        database = new MySQL(getConfig().getString("mysql.user"), getConfig().getString("mysql.database"),
                getConfig().getString("mysql.password"), getConfig().getInt("mysql.port"), getConfig().getString("mysql.hostname"));
        database.openConnection();
        
        String versionString = Utils.Reflection.getVersion();
        if (versionString.equalsIgnoreCase("v1_8_R3")) {
            this.nickWrapper = new NickWrapper1_8_R3();
            this.getServer().getPluginManager().registerEvents(new ItemPickupEvent1_8(this), this);
        } else if (versionString.equalsIgnoreCase("v1_12_R1")) {
            this.nickWrapper = new NickWrapper1_12_R1();
            this.getServer().getPluginManager().registerEvents(new ItemPickupEvent1_12(this), this);
        }
        
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
        this.getCommand("setspawn").setExecutor(tpManager);
        this.getCommand("spawn").setExecutor(tpManager);
        
        this.getCommand("dev").setExecutor(new DevManager(this));
        this.getCommand("signedit").setExecutor(new SignEditManager(this));
        
        PunishmentManager punishmentManager = new PunishmentManager(this);
        this.getCommand("ban").setExecutor(punishmentManager);
        this.getCommand("tempban").setExecutor(punishmentManager);
        this.getCommand("mute").setExecutor(punishmentManager);
        this.getCommand("tempmute").setExecutor(punishmentManager);
        this.getCommand("jail").setExecutor(punishmentManager);
        this.getCommand("setjail").setExecutor(punishmentManager);
        this.getCommand("kick").setExecutor(punishmentManager);
        this.getCommand("warn").setExecutor(punishmentManager);
        this.getCommand("ipban").setExecutor(punishmentManager);
        
        ItemManager itemManager = new ItemManager(this);
        this.getCommand("setname").setExecutor(itemManager);
        this.getCommand("setlore").setExecutor(itemManager);
        
        new BukkitRunnable() {
            public void run() {
                getCommand("vanish").setExecutor(new VanishManager(FirecraftCore.this));
                if (getConfig().contains("spawn")) {
                    World world = Bukkit.getWorld(getConfig().getString("spawn.world"));
                    double x = getConfig().getInt("spawn.x");
                    double y = getConfig().getInt("spawn.y");
                    double z = getConfig().getInt("spawn.z");
                    float yaw = (float) getConfig().getDouble("spawn.yaw");
                    float pitch = (float) getConfig().getDouble("spawn.pitch");
                    serverSpawn = new Location(world, x, y, z, yaw, pitch);
                } else {
                    serverSpawn = Bukkit.getWorlds().get(0).getSpawnLocation();
                }
                
                if (serverSpawn.getWorld() == null) {
                    serverSpawn = Bukkit.getWorlds().get(0).getSpawnLocation();
                }
                
                if (getConfig().contains("jail")) {
                    World world = Bukkit.getWorld(getConfig().getString("jail.world"));
                    double x = getConfig().getInt("jail.x");
                    double y = getConfig().getInt("jail.y");
                    double z = getConfig().getInt("jail.z");
                    float yaw = (float) getConfig().getDouble("jail.yaw");
                    float pitch = (float) getConfig().getDouble("jail.pitch");
                    jailLocation = new Location(world, x, y, z, yaw, pitch);
                }
            }
        }.runTaskLater(this, 10L);
    }
    
    public void onDisable() {
        if (socket != null) {
            socket.sendPacket(new FPacketServerDisconnect(server));
            socket.close();
        }
        
        this.database.closeConnection();
        
        getConfig().set("spawn.world", serverSpawn.getWorld().getName());
        getConfig().set("spawn.x", serverSpawn.getBlockX());
        getConfig().set("spawn.y", serverSpawn.getBlockX());
        getConfig().set("spawn.z", serverSpawn.getBlockX());
        getConfig().set("spawn.yaw", serverSpawn.getYaw());
        getConfig().set("spawn.pitch", serverSpawn.getPitch());
    
        getConfig().set("jail.world", jailLocation.getWorld().getName());
        getConfig().set("jail.x", jailLocation.getBlockX());
        getConfig().set("jail.y", jailLocation.getBlockX());
        getConfig().set("jail.z", jailLocation.getBlockX());
        getConfig().set("jail.yaw", jailLocation.getYaw());
        getConfig().set("jail.pitch", jailLocation.getPitch());
        saveConfig();
    }
    
    @EventHandler
    public void onCommandPreProcess(PlayerCommandPreprocessEvent e) {
        FirecraftPlayer player = Utils.getPlayerFromDatabase(server, database, this, e.getPlayer().getUniqueId());
        ResultSet jailSet = database.querySQL("SELECT * FROM `punishments` WHERE `target`='{uuid}' AND `active`='true' AND `type`='JAIL';".replace("{uuid}", player.getUniqueId().toString().replace("-", "")));
        try {
            if (jailSet.next()) {
                player.sendMessage("&cYou cannot use commands while you are in jail.");
                e.setCancelled(true);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
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
    
    public Location getServerSpawn() {
        return serverSpawn;
    }
    
    public void setServerSpawn(Location serverSpawn) {
        this.serverSpawn = serverSpawn;
    }
    
    public Location getJailLocation() {
        return jailLocation;
    }
    
    public void setJailLocation(Location jailLocation) {
        this.jailLocation = jailLocation;
    }
    
    public MySQL getDatabase() {
        return database;
    }
}