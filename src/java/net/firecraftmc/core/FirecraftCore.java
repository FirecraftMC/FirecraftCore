package net.firecraftmc.core;

import net.firecraftmc.api.FirecraftAPI;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.integrations.FCEconVault;
import net.firecraftmc.api.interfaces.*;
import net.firecraftmc.api.model.Database;
import net.firecraftmc.api.model.FirecraftSocket;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.model.server.FirecraftServer;
import net.firecraftmc.api.packets.*;
import net.firecraftmc.api.packets.staffchat.FPStaffChatQuit;
import net.firecraftmc.api.plugin.IFirecraftCore;
import net.firecraftmc.api.util.Utils;
import net.firecraftmc.api.wrapper.*;
import net.firecraftmc.core.managers.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.util.*;

public class FirecraftCore extends JavaPlugin implements IFirecraftCore {
    
    private IPlayerManager playerManager;
    private NickWrapper nickWrapper;
    private NBTWrapper nbtWrapper;
    private FirecraftSocket socket;
    private FirecraftServer server;
    private Location serverSpawn;
    private Location jailLocation;
    private Database database;
    private HashMap<UUID, String> ackCodes = new HashMap<>();
    private IHomeManager homeManager;
    private IServerManager serverManager;
    private IStaffmodeManager staffmodeManager = null;
    private IWarpManager warpManager = null;
    private IEconomyManager economyManager = null;
    private ICommandManager commandManager = null;
    private MessageManager messageManager = null;
    
    public void onEnable() {
        this.saveDefaultConfig();
        if (!getConfig().contains("host")) {
            getConfig().set("host", "localhost");
            saveConfig();
        }
        
        FirecraftAPI.setFirecraftCore(this);
        
        String host = getConfig().getString("host");
        this.socket = new FirecraftSocket(this, host, getConfig().getInt("port"));
        
        this.socket.addSocketListener((packet) -> {
            if (packet instanceof FPacketServerConnect) {
                FPacketServerConnect serverConnect = (FPacketServerConnect) packet;
                String format = Utils.Chat.formatServerConnect(serverManager.getServer(serverConnect.getServerId()));
                if (playerManager != null) {
                    getPlayerManager().getPlayers().forEach(player -> {
                        if (Rank.isStaff(player.getMainRank())) player.sendMessage(format);
                    });
                }
            } else if (packet instanceof FPacketServerDisconnect) {
                FPacketServerDisconnect serverDisconnect = (FPacketServerDisconnect) packet;
                String format = Utils.Chat.formatServerDisconnect(serverManager.getServer(serverDisconnect.getServerId()));
                getPlayerManager().getPlayers().forEach(player -> {
                    if (Rank.isStaff(player.getMainRank())) player.sendMessage(format);
                });
            }
        });
        
        database = new Database(getConfig().getString("mysql.user"), getConfig().getString("mysql.database"), getConfig().getString("mysql.password"), getConfig().getInt("mysql.port"), getConfig().getString("mysql.hostname"));
        database.openConnection();
        
        if (getConfig().contains("server")) {
            this.server = database.getServer(getConfig().getString("server"));
        }
        
        this.commandManager = new CommandManager(this);
        
        this.registerAllCommands();
        
        this.versionSpecificTasks();
        
        this.postWorldTasks();
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            this.playerManager.addPlayer(this.database.getPlayer(p.getUniqueId()));
            this.playerManager.getPlayer(p.getUniqueId()).playerOnlineStuff();
        }
        
        this.socket.connect();
        this.socket.start();
        if (server != null) {
            this.server.setIp(this.socket.getJavaSocket().getLocalAddress().toString().replace("/", ""));
            this.database.saveServer(server);
        }
        
        new BukkitRunnable() {
            public void run() {
                if (socket.getState().equals(Thread.State.TERMINATED) || !socket.isOpen()) {
                    List<SocketListener> listeners = socket.getSocketListeners();
                    socket = new FirecraftSocket(FirecraftCore.this, host, getConfig().getInt("port"));
                    for (SocketListener listener : listeners) {
                        socket.addSocketListener(listener);
                    }
                    socket.start();
                }
            }
        }.runTaskTimerAsynchronously(this, 0L, 20L);
        
        FCEconVault fcEconVault = new FCEconVault(FirecraftCore.this);
        fcEconVault.registerServices();
    }
    
    public void onDisable() {
        getConfig().set("server", server.getId());
        getConfig().set("spawn", Utils.convertLocationToString(serverSpawn));
        
        if (jailLocation != null) {
            getConfig().set("jail", Utils.convertLocationToString(jailLocation));
        }
        
        this.warpManager.saveWarps();
        
        for (FirecraftPlayer player : playerManager.getPlayers()) {
            this.homeManager.saveHomes(player);
            this.database.savePlayer(player);
            FPStaffChatQuit staffQuit = new FPStaffChatQuit(server.getId(), player.getUniqueId());
            socket.sendPacket(staffQuit);
        }
        
        this.playerManager.getPlayers().clear();
        
        if (socket != null) {
            socket.sendPacket(new FPacketServerDisconnect(server.getId()));
            try {
                socket.close();
            } catch (IOException e) {
            }
        }
        
        server.setIp("");
        database.saveServer(server);
        
        this.database.closeConnection();
        
        saveConfig();
    }
    
    private void registerAllCommands() {
        this.playerManager = new PlayerManager(this);
        new BroadcastManager(this);
        new ChatManager(this);
        new DevManager(this);
        this.economyManager = new EconomyManager(this);
        new FeedManager(this);
        new GamemodeManager(this);
        new HealManager(this);
        this.homeManager = new HomeManager(this);
        new IgnoreManager(this);
        new InventoryManager(this);
        new ItemManager(this);
        new ListManager(this);
        this.messageManager = new MessageManager(this);
        new NickManager(this);
        new ReportManager(this);
        new PunishmentManager(this);
        new SignEditManager(this);
        this.staffmodeManager = new StaffmodeManager(this);
        this.serverManager = new ServerManager(this);
        new TeleportationManager(this);
        new TimeManager(this);
        new WeatherManager(this);
        new VanishManager(this);
        new MailManager(this);
        Utils.Command.registerCommands(this, commandManager, "players", "fct", "ignore", "unignore", "record", "stream");
        Utils.Command.registerCommands(this, commandManager, "list", "stafflist");
        Utils.Command.registerCommands(this, commandManager, "ignore", "unignore");
        Utils.Command.registerCommands(this, commandManager, "chat", "staff", "global", "clearchat", "cc");
        Utils.Command.registerCommands(this, commandManager, "nick", "unnick", "nickrandom");
        Utils.Command.registerCommands(this, commandManager, "gamemode", "gmc", "gms", "gma", "gmsp");
        Utils.Command.registerCommands(this, commandManager, "teleport", "tphere", "back", "tpall", "tpaccept", "tpdeny", "tpa", "setspawn", "spawn");
        this.getCommand("dev").setExecutor(commandManager);
        this.getCommand("feed").setExecutor(commandManager);
        this.getCommand("heal").setExecutor(commandManager);
        this.getCommand("signedit").setExecutor(commandManager);
        Utils.Command.registerCommands(this, commandManager, "punish", "mrules", "ban", "tempban", "mute", "tempmute", "jail", "setjail", "kick", "warn", "ipban", "unban", "unmute", "unjail", "history");
        Utils.Command.registerCommands(this, commandManager, "setname", "setlore");
        this.getCommand("weather").setExecutor(commandManager);
        Utils.Command.registerCommands(this, commandManager, "time", "day", "night");
        Utils.Command.registerCommands(this, commandManager, "broadcast", "socketbroadcast");
        Utils.Command.registerCommands(this, commandManager, "clearinventory", "enderchest", "workbench", "invsee");
        Utils.Command.registerCommands(this, commandManager, "sethome", "delhome", "home");
        getCommand("vanish").setExecutor(commandManager);
        Utils.Command.registerCommands(this, commandManager, "report", "reportadmin");
        Utils.Command.registerCommands(this, commandManager, "message", "reply");
        getCommand("staffmode").setExecutor(commandManager);
        getCommand("firecraftserver").setExecutor(serverManager);
        Utils.Command.registerCommands(this, commandManager, "economy", "pay", "withdraw", "balance", "baltop");
        getCommand("mail").setExecutor(commandManager);
    }
    
    private void versionSpecificTasks() {
        String versionString = Utils.Reflection.getVersion();
        if (versionString.equalsIgnoreCase("v1_8_R3")) {
            this.nickWrapper = new NickWrapper1_8_R3();
            this.nbtWrapper = new NBTWrapper1_8_R3();
            this.getServer().getPluginManager().registerEvents(new ItemPickupEvent1_8(this), this);
        } else if (versionString.equalsIgnoreCase("v1_12_R1")) {
            this.nickWrapper = new NickWrapper1_12_R1();
            this.nbtWrapper = new NBTWrapper1_12_R1();
            this.getServer().getPluginManager().registerEvents(new ItemPickupEvent1_12(this), this);
        }
    }
    
    private void postWorldTasks() {
        new BukkitRunnable() {
            public void run() {
                warpManager = new WarpManager(FirecraftCore.this);
                Utils.Command.registerCommands(FirecraftCore.this, commandManager, "setwarp", "delwarp", "warp");
                serverSpawn = getConfig().contains("spawn") ? Utils.getLocationFromString(getConfig().getString("spawn")) : Bukkit.getWorlds().get(0).getSpawnLocation();
                
                if (serverSpawn == null) {
                    serverSpawn = Bukkit.getWorlds().get(0).getSpawnLocation();
                }
                
                if (getConfig().contains("jail")) {
                    jailLocation = Utils.getLocationFromString(getConfig().getString("jail"));
                }
            }
        }.runTaskLater(this, 10L);
    }
    
    public final NickWrapper getNickWrapper() {
        return nickWrapper;
    }
    
    public final FirecraftSocket getSocket() {
        return socket;
    }
    
    public final IPlayerManager getPlayerManager() {
        return playerManager;
    }
    
    public FirecraftPlayer getPlayer(String name) {
        return playerManager.getPlayer(name);
    }
    
    public FirecraftPlayer getPlayer(UUID uuid) {
        return playerManager.getPlayer(uuid);
    }
    
    public FirecraftServer getFCServer() {
        return server;
    }
    
    public FirecraftServer getFCServer(String id) {
        return serverManager.getServer(id);
    }
    
    public Collection<FirecraftPlayer> getPlayers() {
        return playerManager.getPlayers();
    }
    
    public final Location getSpawn() {
        return serverSpawn;
    }
    
    public final void setSpawn(Location serverSpawn) {
        this.serverSpawn = serverSpawn;
    }
    
    public Location getJailLocation() {
        return jailLocation;
    }
    
    public final void setJailLocation(Location jailLocation) {
        this.jailLocation = jailLocation;
    }
    
    public final Database getFCDatabase() {
        return database;
    }
    
    public final boolean isWarnAcknowledged(UUID uuid) {
        return !this.ackCodes.containsKey(uuid);
    }
    
    public final String getAckCode(UUID uuid) {
        return this.ackCodes.get(uuid);
    }
    
    public final void acknowledgeWarn(UUID uuid, String name) {
        this.ackCodes.remove(uuid);
        this.database.updateSQL("UPDATE `punishments` SET `acknowledged`='true' WHERE `target`='{uuid}' AND `type`='WARN';".replace("{uuid}", uuid.toString()));
        this.socket.sendPacket(new FPacketAcknowledgeWarning(server.getId(), name));
    }
    
    public final void addAckCode(UUID uuid, String code) {
        this.ackCodes.put(uuid, code);
    }
    
    public final IHomeManager getHomeManager() {
        return homeManager;
    }
    
    public final IServerManager getServerManager() {
        return serverManager;
    }
    
    public final void setServer(FirecraftServer server) {
        this.server = server;
    }
    
    public final IStaffmodeManager getStaffmodeManager() {
        return this.staffmodeManager;
    }
    
    public IWarpManager getWarpManager() {
        return warpManager;
    }
    
    public IEconomyManager getEconomyManager() {
        return economyManager;
    }
    
    public NBTWrapper getNbtWrapper() {
        return nbtWrapper;
    }
    
    public FileConfiguration getConfig() {
        return super.getConfig();
    }
    
    public ICommandManager getCommandManager() {
        return commandManager;
    }
    
    public MessageManager getMessageManager() {
        return messageManager;
    }
}