package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.*;
import net.firecraftmc.shared.enums.Rank;
import net.firecraftmc.shared.packets.staffchat.FPSCVanishToggle;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;

import java.util.*;

public class VanishManager implements TabExecutor, Listener {
    
    private final FirecraftCore plugin;
    private final String prefix = "&d&l[Vanish] ";
    private final List<String> interactTypes = Arrays.asList("inventoryinteract", "itemuse", "itempickup", "blockbreak", "blockplace", "entityinteract", "chat", "silentinventoryopen");
    
    public VanishManager(FirecraftCore plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Messages.onlyPlayers);
            return true;
        }
        
        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
        if (player.getMainRank().equals(Rank.VIP) || player.getMainRank().isEqualToOrHigher(Rank.MOD)) {
            if (args.length == 0) {
                if (player.isVanished()) {
                    player.unVanish();
                    
                    for (FirecraftPlayer p : plugin.getPlayerManager().getPlayers()) {
                        p.getPlayer().showPlayer(player.getPlayer());
                        if (!player.isNicked()) {
                            player.getPlayer().setPlayerListName(player.getName());
                        } else {
                            player.getPlayer().setPlayerListName(player.getNick().getNickProfile().getName());
                        }
                        p.getScoreboard().updateField(FirecraftPlayer.FirecraftScoreboard.SBField.PLAYER_COUNT, "§2" + Bukkit.getOnlinePlayers().size() + "§7/§9" + Bukkit.getServer().getMaxPlayers(), "");
                    }
                    player.setActionBar(null);
                } else {
                    player.vanish();
                    for (FirecraftPlayer p : plugin.getPlayerManager().getPlayers()) {
                        if (!player.isNicked()) {
                            player.getPlayer().setPlayerListName(player.getName() + " §7§l[V]");
                        } else {
                            player.getPlayer().setPlayerListName(player.getNick().getNickProfile().getName() + "§7§l[V]");
                        }
                        
                        if (!p.getMainRank().isEqualToOrHigher(player.getMainRank())) {
                            p.getPlayer().hidePlayer(player.getPlayer());
                            p.getScoreboard().updateField(FirecraftPlayer.FirecraftScoreboard.SBField.PLAYER_COUNT, "§2" + (Bukkit.getOnlinePlayers().size() - 1) + "§7/§9" + Bukkit.getServer().getMaxPlayers(), "");
                        }
                    }
                    
                    player.setActionBar(new ActionBar(Messages.actionBar_Vanished));
                }
                FPSCVanishToggle toggleVanish = new FPSCVanishToggle(plugin.getFirecraftServer(), player.getUniqueId());
                plugin.getSocket().sendPacket(toggleVanish);
            } else {
                if (!Utils.Command.checkCmdAliases(args, 0, "settings", "s")) {
                    player.sendMessage(prefix + Messages.invalidSubCommand);
                    return true;
                }
                
                if (!player.isVanished()) {
                    player.sendMessage(prefix + Messages.notVanished);
                    return true;
                }
                
                List<String> toggled = new ArrayList<>(7);
                for (int i = 1; i < args.length; i++) {
                    if (interactTypes.contains(args[i].toLowerCase())) {
                        if (!toggled.contains(args[i].toLowerCase())) {
                            toggled.add(args[i]);
                        } else {
                            player.sendMessage(prefix + Messages.duplicateOption(args[i]));
                        }
                    }
                }
                
                toggled.forEach(option -> {
                    if (option.equalsIgnoreCase("inventoryinteract")) {
                        if (player.getMainRank().isEqualToOrHigher(Rank.MOD)) {
                            player.getVanishInfo().toggleInventoryInteract();
                            player.sendMessage(prefix + Messages.optionToggle("inventoryinteract", player.getVanishInfo().inventoryInteract()));
                        } else {
                            player.sendMessage(prefix + Messages.noPermission);
                        }
                    } else if (option.equalsIgnoreCase("itemuse")) {
                        if (player.getMainRank().equals(Rank.ADMIN) || player.getMainRank().isHigher(Rank.ADMIN)) {
                            player.getVanishInfo().toggleItemUse();
                            player.sendMessage(prefix + Messages.optionToggle("itemuse", player.getVanishInfo().itemUse()));
                        } else {
                            player.sendMessage(prefix + Messages.noPermission);
                        }
                    } else if (option.equalsIgnoreCase("itempickup")) {
                        if (player.getMainRank().equals(Rank.ADMIN) || player.getMainRank().isHigher(Rank.ADMIN)) {
                            player.getVanishInfo().toggleItemPickup();
                            player.sendMessage(prefix + Messages.optionToggle("itempickup", player.getVanishInfo().itemPickup()));
                        } else {
                            player.sendMessage(prefix + Messages.noPermission);
                        }
                    } else if (option.equalsIgnoreCase("blockbreak")) {
                        if (player.getMainRank().equals(Rank.ADMIN) || player.getMainRank().isHigher(Rank.ADMIN)) {
                            player.getVanishInfo().toggleBlockBreak();
                            player.sendMessage(prefix + Messages.optionToggle("blockbreak", player.getVanishInfo().blockBreak()));
                        } else {
                            player.sendMessage(prefix + Messages.noPermission);
                        }
                    } else if (option.equalsIgnoreCase("blockplace")) {
                        if (player.getMainRank().equals(Rank.ADMIN) || player.getMainRank().isHigher(Rank.ADMIN)) {
                            player.getVanishInfo().toggleBlockPlace();
                            player.sendMessage(prefix + Messages.optionToggle("blockplace", player.getVanishInfo().blockPlace()));
                        } else {
                            player.sendMessage(prefix + Messages.noPermission);
                        }
                    } else if (option.equalsIgnoreCase("entityinteract")) {
                        if (player.getMainRank().equals(Rank.ADMIN) || player.getMainRank().isHigher(Rank.ADMIN)) {
                            player.getVanishInfo().toggleEntityInteract();
                            player.sendMessage(prefix + Messages.optionToggle("entityinteract", player.getVanishInfo().entityInteract()));
                            
                        } else {
                            player.sendMessage(prefix + Messages.noPermission);
                        }
                    } else if (option.equalsIgnoreCase("chat")) {
                        player.getVanishInfo().toggleChatInteract();
                        player.sendMessage(prefix + Messages.optionToggle("chatting", player.getVanishInfo().canChat()));
                    } else if (option.equalsIgnoreCase("silentinventoryopen")) {
                        if (player.getMainRank().equals(Rank.MOD) || player.getMainRank().isHigher(Rank.MOD)) {
                            player.getVanishInfo().toggleSilentInventories();
                            player.sendMessage(prefix + Messages.optionToggle("silentinventoryopen", player.getVanishInfo().silentInventoryOpen()));
                        } else {
                            player.sendMessage(prefix + Messages.noPermission);
                        }
                    }
                });
            }
        } else {
            player.sendMessage(prefix + Messages.noPermission);
            return true;
        }
        
        return true;
    }
    
    public List<String> onTabComplete(CommandSender sender, Command cmd, String s, String[] args) {
        if (args.length > 1) {
            if (Utils.Command.checkCmdAliases(args, 0, "settings", "s")) {
                List<String> c = new ArrayList<>();
                for (int i = 1; i < args.length; i++) {
                    for (String it : interactTypes) {
                        if (!args[i].equalsIgnoreCase(it)) {
                            if (it.startsWith(args[i].toLowerCase())) {
                                c.add(it);
                            }
                        }
                    }
                }
                return c;
            }
        }
        return null;
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(e.getPlayer().getUniqueId());
        if (player.isVanished()) {
            if (!player.getVanishInfo().blockBreak()) {
                e.setCancelled(true);
                player.sendMessage(prefix + Messages.cannotActionVanished("break blocks"));
            }
        }
    }
    
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(e.getPlayer().getUniqueId());
        if (player.isVanished()) {
            if (!player.getVanishInfo().blockPlace()) {
                e.setCancelled(true);
                player.sendMessage(prefix + Messages.cannotActionVanished("place blocks"));
            }
        }
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(e.getPlayer().getUniqueId());
        if (e.getClickedBlock() != null && e.getClickedBlock().getType().equals(Material.CHEST)) {
            if (player.isVanished()) {
                if (!player.getVanishInfo().inventoryInteract()) {
                    e.setCancelled(true);
                    player.sendMessage(prefix + Messages.cannotActionVanished("open inventories"));
                } else {
                    if (player.getVanishInfo().silentInventoryOpen()) {
                        e.setCancelled(true);
                        Inventory inv = null;
                        //This is read only, need to use sync tasks with the copy and original to get read/write and it updating, will do in the future
                        //https://gyazo.com/8e1b19e1ce2acc62abcc54749da97486
                        if (e.getClickedBlock().getState() instanceof Chest) {
                            Chest chest = (Chest) e.getClickedBlock().getState();
                            int totalAmount;
                            if (chest.getInventory() instanceof DoubleChestInventory) {
                                inv = Bukkit.getServer().createInventory(null, 54, "Chest");
                                totalAmount = 54;
                            } else {
                                inv = Bukkit.getServer().createInventory(null, 27, "Chest");
                                totalAmount = 27;
                            }
                            
                            for (int i = 0; i < totalAmount; i++) {
                                ItemStack item = chest.getInventory().getItem(i);
                                if (item != null) {
                                    inv.setItem(i, item);
                                }
                            }
                        }
                        
                        player.getPlayer().openInventory(inv);
                        player.sendMessage(prefix + Messages.openInventorySilent);
                    }
                }
            }
        }
    }
    
    
    @EventHandler
    public void onEntityDamageEvent(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player) {
            FirecraftPlayer player = plugin.getPlayerManager().getPlayer(e.getDamager().getUniqueId());
            if (player.isVanished()) {
                if (!player.getVanishInfo().entityInteract()) {
                    e.setCancelled(true);
                    player.sendMessage(prefix + Messages.cannotActionVanished("damage entities"));
                }
            }
        }
    }
    
    @EventHandler
    public void onPlayerBucketFill(PlayerBucketFillEvent e) {
        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(e.getPlayer().getUniqueId());
        if (player.isVanished()) {
            if (!player.getVanishInfo().itemUse()) {
                e.setCancelled(true);
                player.sendMessage(prefix + Messages.cannotActionVanished("fill buckets"));
            }
        }
    }
    
    @EventHandler
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent e) {
        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(e.getPlayer().getUniqueId());
        if (player.isVanished()) {
            if (!player.getVanishInfo().itemUse()) {
                e.setCancelled(true);
                player.sendMessage(prefix + Messages.cannotActionVanished("empty buckets"));
            }
        }
    }
    
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent e) {
        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(e.getPlayer().getUniqueId());
        if (player.isVanished()) {
            if (!player.getVanishInfo().itemPickup()) {
                e.setCancelled(true);
                player.sendMessage(prefix + Messages.cannotActionVanished("drop items"));
            }
        }
    }
    
    @EventHandler
    public void onPlayerEntityInteract(PlayerInteractEntityEvent e) {
        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(e.getPlayer().getUniqueId());
        if (player.isVanished()) {
            if (!player.getVanishInfo().entityInteract()) {
                e.setCancelled(true);
                player.sendMessage(prefix + Messages.cannotActionVanished("interact with entities"));
            }
        }
    }
    
    @EventHandler
    public void onPlayerEntityShear(PlayerShearEntityEvent e) {
        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(e.getPlayer().getUniqueId());
        if (player.isVanished()) {
            if (!player.getVanishInfo().entityInteract()) {
                e.setCancelled(true);
                player.sendMessage(prefix + Messages.cannotActionVanished("shear entities"));
            }
        }
    }
}