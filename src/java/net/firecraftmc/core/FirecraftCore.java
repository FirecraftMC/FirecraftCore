package net.firecraftmc.core;

import net.firecraftmc.core.managers.*;
import net.firecraftmc.core.wrapper.ItemPickupEvent1_12;
import net.firecraftmc.core.wrapper.ItemPickupEvent1_8;
import net.firecraftmc.core.wrapper.NickWrapper1_12_R1;
import net.firecraftmc.core.wrapper.NickWrapper1_8_R3;
import net.firecraftmc.shared.MySQL;
import net.firecraftmc.shared.classes.*;
import net.firecraftmc.shared.packets.FPacketAcknowledgeWarning;
import net.firecraftmc.shared.packets.FPacketServerDisconnect;
import net.firecraftmc.shared.packets.staffchat.FPStaffChatQuit;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.UUID;

public class FirecraftCore extends FirecraftPlugin implements Listener {
    private PlayerManager playerManager;
    private NickWrapper nickWrapper;
    private FirecraftSocket socket;
    private FirecraftServer server;
    private Location serverSpawn;
    private Location jailLocation;
    private MySQL database;
    private final HashMap<UUID, String> ackCodes = new HashMap<>();
    private HomeManager homeManager;
    private WarpManager warpManager;

    public void onEnable() {
        instance = this;
        this.saveDefaultConfig();
        if (!getConfig().contains("host")) {
            getConfig().set("host", "172.18.0.2");
            saveConfig();
        }
    
        this.playerManager = new PlayerManager(this);
        Utils.Command.registerCommands(this, playerManager, "players", "fct");
        
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
        Utils.Command.registerCommands(this, new NickManager(this), "nick", "nickcancel", "nickconfirm", "unnick");
        Utils.Command.registerCommands(this, new GamemodeManager(this), "gamemode", "gmc", "gms", "gma", "gmsp");
        Utils.Command.registerCommands(this, new TeleportationManager(this), "teleport", "tphere", "back", "tpall", "tpaccept", "tpdeny", "tpa", "setspawn", "spawn");
        this.getCommand("dev").setExecutor(new DevManager(this));
        this.getCommand("signedit").setExecutor(new SignEditManager(this));
        Utils.Command.registerCommands(this, new PunishmentManager(this), "ban", "tempban", "mute", "tempmute", "jail", "setjail", "kick", "warn", "ipban", "unban", "unmute", "unjail");
        Utils.Command.registerCommands(this, new ItemManager(this), "setname", "setlore");
        this.getCommand("weather").setExecutor(new WeatherManager(this));
        Utils.Command.registerCommands(this, new TimeManager(this), "time", "day", "night");
        Utils.Command.registerCommands(this, new BroadcastManager(this), "broadcast", "socketbroadcast");
        Utils.Command.registerCommands(this, new InventoryManager(this), "clearinventory", "enderchest", "workbench", "invsee");
        this.homeManager = new HomeManager(this);
        Utils.Command.registerCommands(this, this.homeManager, "sethome", "delhome", "home");
        getCommand("vanish").setExecutor(new VanishManager(this));

        new BukkitRunnable() {
            public void run() {
                warpManager = new WarpManager(FirecraftCore.this);
                Utils.Command.registerCommands(FirecraftCore.this, warpManager, "setwarp", "delwarp", "warp");
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
        for (FirecraftPlayer player : playerManager.getPlayers()) {
            FPStaffChatQuit staffQuit = new FPStaffChatQuit(server, player.getUniqueId());
            socket.sendPacket(staffQuit);
        }
        
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
    
        if (jailLocation != null) {
            getConfig().set("jail.world", jailLocation.getWorld().getName());
            getConfig().set("jail.x", jailLocation.getBlockX());
            getConfig().set("jail.y", jailLocation.getBlockX());
            getConfig().set("jail.z", jailLocation.getBlockX());
            getConfig().set("jail.yaw", jailLocation.getYaw());
            getConfig().set("jail.pitch", jailLocation.getPitch());
        }

        this.warpManager.saveWarps();

        for (FirecraftPlayer player : playerManager.getPlayers()) {
            this.homeManager.saveHomes(player);
        }

        saveConfig();
    }
    
    @EventHandler
    public void onCommandPreProcess(PlayerCommandPreprocessEvent e) {
        FirecraftPlayer player = Utils.Database.getPlayerFromDatabase(server, database, e.getPlayer().getUniqueId());
        ResultSet jailSet = database.querySQL("SELECT * FROM `punishments` WHERE `target`='{uuid}' AND `active`='true' AND `type`='JAIL';".replace("{uuid}", player.getUniqueId().toString().replace("-", "")));
        try {
            if (jailSet.next()) {
                player.sendMessage(Messages.jailedNoCmds);
                e.setCancelled(true);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    
        ResultSet warnSet = database.querySQL("SELECT * FROM `punishments` WHERE `target`='{uuid}' AND `acknowledged`='false' AND `type`='WARN';".replace("{uuid}", player.getUniqueId().toString().replace("-", "")));
        try {
            if (warnSet.next()) {
                player.sendMessage(Messages.unAckWarnNoCmds);
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
    
    public boolean isWarnAcknowledged(UUID uuid) {
        return !this.ackCodes.containsKey(uuid);
    }
    
    public String getAckCode(UUID uuid) {
        return this.ackCodes.get(uuid);
    }
    
    public void acknowledgeWarn(UUID uuid, String name) {
        this.ackCodes.remove(uuid);
        this.database.updateSQL("UPDATE `punishments` SET `acknowledged`='true' WHERE `target`='{uuid}' AND `type`='WARN';".replace("{uuid}", uuid.toString().replace("-", "")));
        this.socket.sendPacket(new FPacketAcknowledgeWarning(server, name));
    }
    
    public void addAckCode(UUID uuid, String code) {
        this.ackCodes.put(uuid, code);
    }

    public HomeManager getHomeManager() {
        return homeManager;
    }
}