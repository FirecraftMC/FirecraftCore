package net.firecraftmc.core.wrapper;

import com.mojang.authlib.properties.Property;
import net.firecraftmc.shared.classes.*;
import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.CraftServer;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class NickWrapper1_12_R1 extends NickWrapper {
    private MinecraftServer minecraftServer;

    private final PacketPlayOutPlayerInfo.EnumPlayerInfoAction action_remove = PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER;
    private final PacketPlayOutPlayerInfo.EnumPlayerInfoAction action_add = PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER;

    public NickWrapper1_12_R1() { this.minecraftServer = ((CraftServer) Bukkit.getServer()).getServer(); }

    public void refreshOthers(FirecraftPlugin plugin, Player player, String name) {
        List<Player> canSee = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.canSee(player)) {
                canSee.add(p);
                p.hidePlayer(player);
            }
        }

        for (Player p : canSee) {
            p.showPlayer(player);
        }
    }

    public void setProfileName(Player player, String name) {
        CraftPlayer craftPlayer = (CraftPlayer) player;
        try {
            Field nameField = craftPlayer.getProfile().getClass().getDeclaredField("name");
            nameField.setAccessible(true);
            nameField.set(craftPlayer.getProfile(), name);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private void setSkinProperties(Player player, Skin skin) {
        CraftPlayer craftPlayer = (CraftPlayer) player;
        craftPlayer.getProfile().getProperties().clear();
        craftPlayer.getProfile().getProperties().put(skin.getName(), new Property(skin.getName(), skin.getValue(), skin.getSignature()));
    }

    public void refreshSelf(FirecraftPlugin plugin, Player nicked, String name) {
        CraftPlayer craftPlayer = (CraftPlayer) nicked;
        PacketPlayOutPlayerInfo removePlayer = new PacketPlayOutPlayerInfo(action_remove, craftPlayer.getHandle());
        PacketPlayOutPlayerInfo addPlayer = new PacketPlayOutPlayerInfo(action_add, craftPlayer.getHandle());
        PacketPlayOutRespawn respawnPlayer = new PacketPlayOutRespawn(0, minecraftServer.getDifficulty(), WorldType.types[0], craftPlayer.getHandle().playerInteractManager.getGameMode());

        PlayerConnection connection = craftPlayer.getHandle().playerConnection;
        connection.sendPacket(removePlayer);

        new BukkitRunnable() {
            public void run() {
                boolean flying = nicked.isFlying();
                Location location = nicked.getLocation();
                int level = nicked.getLevel();
                float xp = nicked.getExp();
                double health = nicked.getHealth();

                connection.sendPacket(respawnPlayer);

                nicked.setFlying(flying);
                nicked.teleport(location);
                nicked.updateInventory();
                nicked.setLevel(level);
                nicked.setExp(xp);
                nicked.setHealth(health);

                connection.sendPacket(addPlayer);
            }
        }.runTaskLater(plugin, 2L);
    }

    public FirecraftPlayer.NickInfo setNick(FirecraftPlugin plugin, FirecraftPlayer player, FirecraftPlayer nickProfile) {
        setProfileName(player.getPlayer(), nickProfile.getName());
        setSkinProperties(player.getPlayer(), nickProfile.getSkin());
        refreshOthers(plugin, player.getPlayer(), nickProfile.getName());
        refreshSelf(plugin, player.getPlayer(), nickProfile.getName());

        return new FirecraftPlayer.NickInfo(nickProfile);
    }
}
