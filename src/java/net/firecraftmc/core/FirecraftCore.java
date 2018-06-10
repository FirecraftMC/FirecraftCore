package net.firecraftmc.core;

import net.firecraftmc.core.managers.*;
import net.firecraftmc.shared.classes.model.Database;
import net.firecraftmc.shared.classes.model.player.FirecraftPlayer;
import net.firecraftmc.shared.classes.model.FirecraftServer;
import net.firecraftmc.shared.classes.model.FirecraftSocket;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.classes.abstraction.FirecraftPlugin;
import net.firecraftmc.shared.classes.wrapper.ItemPickupEvent1_12;
import net.firecraftmc.shared.classes.wrapper.ItemPickupEvent1_8;
import net.firecraftmc.shared.classes.wrapper.NickWrapper1_12_R1;
import net.firecraftmc.shared.classes.wrapper.NickWrapper1_8_R3;
import net.firecraftmc.shared.packets.FPacketServerDisconnect;
import net.firecraftmc.shared.packets.staffchat.FPStaffChatQuit;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * The main plugin class for the core and where a lot of the implementation for FirecraftPlugin and other FirecraftShared stuff is.
 */
public class FirecraftCore extends FirecraftPlugin {

    private WarpManager warpManager;

    /**
     * This is where all of the stuff is loaded into memory and the task methods are called.
     */
    public void onEnable() {
        instance = this;
        this.saveDefaultConfig();
        if (!getConfig().contains("host")) {
            getConfig().set("host", "172.18.0.2");
            saveConfig();
        }

        String host = getConfig().getString("host");
        this.socket = new FirecraftSocket(this, host, getConfig().getInt("port"));
        this.socket.start();
        this.server = new FirecraftServer(getConfig().getString("server.name"), ChatColor.valueOf(getConfig().getString("server.color")));
        new BukkitRunnable() {
            public void run() {
                if (socket == null || socket.getState().equals(Thread.State.TERMINATED) || !socket.isOpen()) {
                    socket = new FirecraftSocket(FirecraftCore.this, host, getConfig().getInt("port"));
                }
            }
        }.runTaskTimerAsynchronously(this, 0L, 20L);

        this.registerAllCommands();

        database = new Database(getConfig().getString("mysql.user"), getConfig().getString("mysql.database"),
                getConfig().getString("mysql.password"), getConfig().getInt("mysql.port"), getConfig().getString("mysql.hostname"));
        database.openConnection();

        this.versionSpecificTasks();

        this.postWorldTasks();

        for (Player p : Bukkit.getOnlinePlayers()) {
            this.playerManager.addPlayer(this.database.getPlayer(server, p.getUniqueId()));
            this.playerManager.getPlayer(p.getUniqueId()).playerOnlineStuff();
        }
    }

    /**
     * All close methods are called here and stuff is saved to the database/files where needed.
     */
    public void onDisable() {
        getConfig().set("spawn", Utils.convertLocationToString(serverSpawn));

        if (jailLocation != null) {
            getConfig().set("jail", Utils.convertLocationToString(jailLocation));
        }

        this.warpManager.saveWarps();

        for (FirecraftPlayer player : playerManager.getPlayers()) {
            this.homeManager.saveHomes(player);
            this.database.savePlayer(player);
            FPStaffChatQuit staffQuit = new FPStaffChatQuit(server, player.getUniqueId());
            socket.sendPacket(staffQuit);
        }

        this.playerManager.getPlayers().clear();

        if (socket != null) {
            socket.sendPacket(new FPacketServerDisconnect(server));
            socket.close();
        }

        this.database.closeConnection();

        saveConfig();
    }

    /**
     * This method handles the registration of all commands and their managers.
     */
    private void registerAllCommands() {
        this.playerManager = new PlayerManager(this);
        Utils.Command.registerCommands(this, playerManager, "players", "fct", "list", "ignore", "unignore", "record", "stafflist");
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
        Utils.Command.registerCommands(this, new ReportManager(this), "report", "reportadmin");
        Utils.Command.registerCommands(this, new MessageManager(this), "message", "reply");
    }

    /**
     * Handles the Version Specific classes needed for proper operation.
     */
    private void versionSpecificTasks() {
        String versionString = Utils.Reflection.getVersion();
        if (versionString.equalsIgnoreCase("v1_8_R3")) {
            this.nickWrapper = new NickWrapper1_8_R3();
            this.getServer().getPluginManager().registerEvents(new ItemPickupEvent1_8(this), this);
        } else if (versionString.equalsIgnoreCase("v1_12_R1")) {
            this.nickWrapper = new NickWrapper1_12_R1();
            this.getServer().getPluginManager().registerEvents(new ItemPickupEvent1_12(this), this);
        }
    }

    /**
     * Handles Post-World Tasks that can only be done when the world is loaded into memory (Plugin is enabled on Startup)
     */
    private void postWorldTasks() {
        new BukkitRunnable() {
            public void run() {
                warpManager = new WarpManager(FirecraftCore.this);
                Utils.Command.registerCommands(FirecraftCore.this, warpManager, "setwarp", "delwarp", "warp");
                if (getConfig().contains("spawn")) {
                    serverSpawn = Utils.getLocationFromString(getConfig().getString("spawn"));
                } else {
                    serverSpawn = Bukkit.getWorlds().get(0).getSpawnLocation();
                }

                if (serverSpawn == null) {
                    serverSpawn = Bukkit.getWorlds().get(0).getSpawnLocation();
                }

                if (getConfig().contains("jail")) {
                    jailLocation = Utils.getLocationFromString(getConfig().getString("jail"));
                }
            }
        }.runTaskLater(this, 10L);
    }
}