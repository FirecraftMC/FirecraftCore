package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.Prefixes;
import net.firecraftmc.shared.classes.model.player.FirecraftPlayer;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.classes.enums.Rank;
import net.firecraftmc.shared.classes.model.ActionBar;
import net.firecraftmc.shared.packets.staffchat.FPSCVanishToggle;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VanishManager implements TabExecutor, Listener {
    
    private final FirecraftCore plugin;
    private final List<String> interactTypes = Arrays.asList("inventoryinteract", "itemuse", "itempickup", "blockbreak", "blockplace", "entityinteract", "chat", "silentinventoryopen");
    
    public VanishManager(FirecraftCore plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        plugin.getSocket().addSocketListener(packet -> {
            FPSCVanishToggle toggleVanish = ((FPSCVanishToggle) packet);
            FirecraftPlayer staffMember = plugin.getPlayerManager().getPlayer(toggleVanish.getPlayer());
            String format = Utils.Chat.formatVanishToggle(toggleVanish.getServer(), staffMember, staffMember.isVanished());
            Utils.Chat.sendStaffChatMessage(plugin.getPlayerManager().getPlayers(), staffMember, format);
        });
    }
    
    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Messages.onlyPlayers);
            return true;
        }
        
        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());
        if (player.getMainRank().equals(Rank.VIP) || player.getMainRank().isEqualToOrHigher(Rank.MODERATOR)) {
            if (player.isRecording()) {
                player.sendMessage(Messages.recordingNoUse);
                return true;
            }
            if (args.length == 0) {
                if (player.isVanished()) {
                    boolean flight = player.getVanishInfo().allowFlightBeforeVanish();
                    player.unVanish();
                    
                    for (FirecraftPlayer p : plugin.getPlayerManager().getPlayers()) {
                        p.getPlayer().showPlayer(player.getPlayer());
                        if (!player.isNicked()) {
                            player.getPlayer().setPlayerListName(player.getName());
                        } else {
                            player.getPlayer().setPlayerListName(player.getNick().getProfile().getName());
                        }
                        p.getScoreboard().updateScoreboard(p);
                    }
                    player.setActionBar(null);
                    player.getPlayer().setAllowFlight(flight);
                } else {
                    player.vanish();
                    for (FirecraftPlayer p : plugin.getPlayerManager().getPlayers()) {
                        if (!player.isNicked()) {
                            player.getPlayer().setPlayerListName(player.getName() + " §7§l[V]");
                        } else {
                            player.getPlayer().setPlayerListName(player.getNick().getProfile().getName() + "§7§l[V]");
                        }
                        
                        if (!p.getMainRank().isEqualToOrHigher(player.getMainRank())) {
                            p.getPlayer().hidePlayer(player.getPlayer());
                            p.getScoreboard().updateScoreboard(p);
                        }
                    }
                    
                    player.setActionBar(new ActionBar(Messages.actionBar_Vanished));
                    player.getVanishInfo().setAllowFlightBeforeVanish(player.getPlayer().getAllowFlight());
                    player.getPlayer().setAllowFlight(true);
                }
                FPSCVanishToggle toggleVanish = new FPSCVanishToggle(plugin.getFirecraftServer(), player.getUniqueId());
                plugin.getSocket().sendPacket(toggleVanish);
            } else {
                if (!Utils.Command.checkCmdAliases(args, 0, "settings", "s")) {
                    player.sendMessage(Prefixes.VANISH + Messages.invalidSubCommand);
                    return true;
                }
                
                if (!player.isVanished()) {
                    player.sendMessage(Prefixes.VANISH + Messages.notVanished);
                    return true;
                }
                
                List<String> toggled = new ArrayList<>(7);
                for (int i = 1; i < args.length; i++) {
                    if (interactTypes.contains(args[i].toLowerCase())) {
                        if (!toggled.contains(args[i].toLowerCase())) {
                            toggled.add(args[i]);
                        } else {
                            player.sendMessage(Prefixes.VANISH + Messages.duplicateOption(args[i]));
                        }
                    }
                }
                
                toggled.forEach(option -> {
                    if (option.equalsIgnoreCase("inventoryinteract")) {
                        if (player.getMainRank().isEqualToOrHigher(Rank.MODERATOR)) {
                            player.getVanishInfo().toggleInventoryInteract();
                            player.sendMessage(Prefixes.VANISH + Messages.optionToggle("inventoryinteract", player.getVanishInfo().inventoryInteract()));
                        } else {
                            player.sendMessage(Prefixes.VANISH + Messages.noPermission);
                        }
                    } else if (option.equalsIgnoreCase("itemuse")) {
                        if (player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                            player.getVanishInfo().toggleItemUse();
                            player.sendMessage(Prefixes.VANISH + Messages.optionToggle("itemuse", player.getVanishInfo().itemUse()));
                        } else {
                            player.sendMessage(Prefixes.VANISH + Messages.noPermission);
                        }
                    } else if (option.equalsIgnoreCase("itempickup")) {
                        if (player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                            player.getVanishInfo().toggleItemPickup();
                            player.sendMessage(Prefixes.VANISH + Messages.optionToggle("itempickup", player.getVanishInfo().itemPickup()));
                        } else {
                            player.sendMessage(Prefixes.VANISH + Messages.noPermission);
                        }
                    } else if (option.equalsIgnoreCase("blockbreak")) {
                        if (player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                            player.getVanishInfo().toggleBlockBreak();
                            player.sendMessage(Prefixes.VANISH + Messages.optionToggle("blockbreak", player.getVanishInfo().blockBreak()));
                        } else {
                            player.sendMessage(Prefixes.VANISH + Messages.noPermission);
                        }
                    } else if (option.equalsIgnoreCase("blockplace")) {
                        if (player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                            player.getVanishInfo().toggleBlockPlace();
                            player.sendMessage(Prefixes.VANISH + Messages.optionToggle("blockplace", player.getVanishInfo().blockPlace()));
                        } else {
                            player.sendMessage(Prefixes.VANISH + Messages.noPermission);
                        }
                    } else if (option.equalsIgnoreCase("entityinteract")) {
                        if (player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                            player.getVanishInfo().toggleEntityInteract();
                            player.sendMessage(Prefixes.VANISH + Messages.optionToggle("entityinteract", player.getVanishInfo().entityInteract()));
                            
                        } else {
                            player.sendMessage(Prefixes.VANISH + Messages.noPermission);
                        }
                    } else if (option.equalsIgnoreCase("chat")) {
                        player.getVanishInfo().toggleChatInteract();
                        player.sendMessage(Prefixes.VANISH + Messages.optionToggle("chatting", player.getVanishInfo().canChat()));
                    } else if (option.equalsIgnoreCase("silentinventoryopen")) {
                        if (player.getMainRank().isEqualToOrHigher(Rank.MODERATOR)) {
                            player.getVanishInfo().toggleSilentInventories();
                            player.sendMessage(Prefixes.VANISH + Messages.optionToggle("silentinventoryopen", player.getVanishInfo().silentInventoryOpen()));
                        } else {
                            player.sendMessage(Prefixes.VANISH + Messages.noPermission);
                        }
                    }
                });
            }
        } else {
            player.sendMessage(Prefixes.VANISH + Messages.noPermission);
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
                player.sendMessage(Prefixes.VANISH + Messages.cannotActionVanished("break blocks"));
            }
        }
    }
    
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(e.getPlayer().getUniqueId());
        if (player.isVanished()) {
            if (!player.getVanishInfo().blockPlace()) {
                e.setCancelled(true);
                player.sendMessage(Prefixes.VANISH + Messages.cannotActionVanished("place blocks"));
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
                    player.sendMessage(Prefixes.VANISH + Messages.cannotActionVanished("open inventories"));
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
                        player.sendMessage(Prefixes.VANISH + Messages.openInventorySilent);
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
                    player.sendMessage(Prefixes.VANISH + Messages.cannotActionVanished("damage entities"));
                }
            }
        }
        if (e.getEntity() instanceof Player) {
            FirecraftPlayer player = plugin.getPlayerManager().getPlayer(e.getEntity().getUniqueId());
            if (player.isVanished()) {
                e.setCancelled(true);
            }
        }
    }
    
    @EventHandler
    public void onEntityTarget(EntityTargetLivingEntityEvent e) {
        if (e.getTarget() instanceof Player) {
            FirecraftPlayer player = plugin.getPlayerManager().getPlayer(e.getTarget().getUniqueId());
            if (player.isVanished()) {
                e.setCancelled(true);
            }
        }
    }
    
    @EventHandler
    public void onPlayerBucketFill(PlayerBucketFillEvent e) {
        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(e.getPlayer().getUniqueId());
        if (player.isVanished()) {
            if (!player.getVanishInfo().itemUse()) {
                e.setCancelled(true);
                player.sendMessage(Prefixes.VANISH + Messages.cannotActionVanished("fill buckets"));
            }
        }
    }
    
    @EventHandler
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent e) {
        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(e.getPlayer().getUniqueId());
        if (player.isVanished()) {
            if (!player.getVanishInfo().itemUse()) {
                e.setCancelled(true);
                player.sendMessage(Prefixes.VANISH + Messages.cannotActionVanished("empty buckets"));
            }
        }
    }
    
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent e) {
        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(e.getPlayer().getUniqueId());
        if (player.isVanished()) {
            if (!player.getVanishInfo().itemPickup()) {
                e.setCancelled(true);
                player.sendMessage(Prefixes.VANISH + Messages.cannotActionVanished("drop items"));
            }
        }
    }
    
    @EventHandler
    public void onPlayerEntityInteract(PlayerInteractEntityEvent e) {
        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(e.getPlayer().getUniqueId());
        if (player.isVanished()) {
            if (!player.getVanishInfo().entityInteract()) {
                e.setCancelled(true);
                player.sendMessage(Prefixes.VANISH + Messages.cannotActionVanished("interact with entities"));
            }
        }
    }
    
    @EventHandler
    public void onPlayerEntityShear(PlayerShearEntityEvent e) {
        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(e.getPlayer().getUniqueId());
        if (player.isVanished()) {
            if (!player.getVanishInfo().entityInteract()) {
                e.setCancelled(true);
                player.sendMessage(Prefixes.VANISH + Messages.cannotActionVanished("shear entities"));
            }
        }
    }
}