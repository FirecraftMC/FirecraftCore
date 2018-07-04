package net.firecraftmc.core.managers;

import net.firecraftmc.core.FirecraftCore;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.Prefixes;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.classes.enums.Rank;
import net.firecraftmc.shared.classes.enums.TransactionType;
import net.firecraftmc.shared.classes.interfaces.IEconomyManager;
import net.firecraftmc.shared.classes.model.Transaction;
import net.firecraftmc.shared.classes.model.player.FirecraftPlayer;
import net.firecraftmc.shared.classes.model.player.FirecraftProfile;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class EconomyManager implements IEconomyManager {

    private FirecraftCore plugin;
    public EconomyManager(FirecraftCore plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onItemClick(PlayerInteractEvent e) {
        if (e.getAction().equals(Action.RIGHT_CLICK_AIR)) {
            if (e.getItem() != null) {
                if (e.getItem().getItemMeta() != null) {
                    if (e.getItem().getItemMeta().getDisplayName() != null) {
                        if (e.getItem().getItemMeta().getDisplayName().toLowerCase().contains("bank note")) {
                            //TODO Temporary until the use of ItemStack nbt
                            e.setCancelled(true);
                            String[] a = e.getItem().getItemMeta().getDisplayName().split("\\$");
                            double amount = Double.parseDouble(a[1]);
                            FirecraftPlayer player = plugin.getPlayerManager().getPlayer(e.getPlayer().getUniqueId());
                            Transaction deposit = deposit(player.getProfile(), amount);
                            player.sendMessage(Prefixes.ECONOMY + "<nc>You redeemed a bank note worth <vc>$" + amount);
                            plugin.getFCDatabase().saveTransaction(deposit);
                            player.getInventory().remove(e.getItem());
                        }
                    }
                }
            }
        }
    }

    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Prefixes.ECONOMY + Messages.onlyPlayers);
            return true;
        }

        FirecraftPlayer player = plugin.getPlayerManager().getPlayer(((Player) sender).getUniqueId());

        if (cmd.getName().equalsIgnoreCase("economy")) {
            if (!player.getMainRank().isEqualToOrHigher(Rank.ADMIN)) {
                player.sendMessage(Prefixes.ECONOMY + Messages.noPermission);
                return true;
            }

            if (!(args.length > 0)) {
                player.sendMessage(Prefixes.ECONOMY + "<ec>Usage: /economy <subcommand> <arguments>");
                return true;
            }

            if (Utils.Command.checkCmdAliases(args, 0, "give", "g")) {
                if (!(args.length > 2)) {
                    player.sendMessage(Prefixes.ECONOMY + "<ec>Usage: /economy give <player> <amount>");
                    return true;
                }

                FirecraftPlayer target = plugin.getPlayerManager().getPlayer(args[1]);

                double amount;
                try {
                    amount = Double.parseDouble(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage(Prefixes.ECONOMY + "<ec>The amount " + args[2] + " is not a valid number.");
                    return true;
                }

                Transaction transaction = deposit(target.getProfile(), amount);
                transaction.setAdmin(player.getUniqueId());
                player.sendMessage(Prefixes.ECONOMY + "<nc>You gave <vc>$" + amount + " <nc>to <vc>" + target.getName() + "<nc>'s account.");
                if (target.isOnline()) {
                    target.sendMessage(Prefixes.ECONOMY + "<nc>You were given <vc>$" + amount + " <nc>by <vc>" + player.getName());
                }
                plugin.getFCDatabase().saveTransaction(transaction);
            } else if (Utils.Command.checkCmdAliases(args, 0, "take", "t")) {
                if (!(args.length > 2)) {
                    player.sendMessage(Prefixes.ECONOMY + "<ec>Usage: /economy take <player> <amount>");
                    return true;
                }

                FirecraftPlayer target = plugin.getPlayerManager().getPlayer(args[1]);

                double amount;
                try {
                    amount = Double.parseDouble(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage(Prefixes.ECONOMY + "<ec>The amount " + args[2] + " is not a valid number.");
                    return true;
                }

                Transaction transaction = withdraw(target.getProfile(), amount);
                transaction.setAdmin(player.getUniqueId());
                player.sendMessage(Prefixes.ECONOMY + "<nc>You took <vc>$" + amount + " <nc>from <vc>" + target.getName() + "<nc>'s account.");
                if (target.isOnline()) {
                    target.sendMessage(Prefixes.ECONOMY + "<vc>" + player.getName() + " <nc>took <vc>$" + amount + " <nc>from your account");
                }
                plugin.getFCDatabase().saveTransaction(transaction);
            }
        } else if (cmd.getName().equalsIgnoreCase("pay")) {
            if (!(args.length == 2)) {
                player.sendMessage(Prefixes.ECONOMY + "<ec>You must provide a player and an amount.");
                return true;
            }

            FirecraftPlayer target = plugin.getPlayerManager().getPlayer(args[0]);
            double amount;
            try {
                amount = Double.parseDouble(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(Prefixes.ECONOMY + "<ec>The amount " + args[1] + " is not a valid number.");
                return true;
            }

            if (player.getBalance() < amount) {
                player.sendMessage(Prefixes.ECONOMY + "<ec>You do not have the funds to pay " + target.getName() + " the amount of $" + amount);
                return true;
            }

            Transaction withdrawal = withdraw(player.getProfile(), amount);
            Transaction deposit = deposit(target.getProfile(), amount);
            withdrawal.setTarget(target.getUniqueId());
            deposit.setTarget(player.getUniqueId());
            player.sendMessage(Prefixes.ECONOMY + "<nc>You paid <vc>$" + amount + " <nc>to <vc>" + target.getName());
            target.sendMessage(Prefixes.ECONOMY + "<vc>" + player.getName() + " <nc>paid you <vc>$" + amount);
            plugin.getFCDatabase().saveTransaction(withdrawal);
            plugin.getFCDatabase().saveTransaction(deposit);
        } else if (cmd.getName().equalsIgnoreCase("withdraw")) {
            //TODO Economy Ticket system with unique ids Should be something that connects to the database, probably in it's own class with listeners eventually
            if (!(args.length == 1)) {
                player.sendMessage(Prefixes.ECONOMY + "<ec>Usage: /withdraw <amount>");
                return true;
            }

            double amount;
            try {
                amount = Double.parseDouble(args[0]);
            } catch (NumberFormatException e) {
                player.sendMessage(Prefixes.ECONOMY + "<ec>The amount " + args[0] + " is not a valid number.");
                return true;
            }

            if (player.getBalance() < amount) {
                player.sendMessage(Prefixes.ECONOMY + "<ec>You do not have the funds to withdraw $" + amount);
                return true;
            }

            Transaction withdraw = withdraw(player.getProfile(), amount);
            ItemStack itemStack = new ItemStack(Material.PAPER, 1);
            itemStack.addUnsafeEnchantment(Enchantment.ARROW_DAMAGE, 1);
            ItemMeta meta = itemStack.getItemMeta();
            meta.setDisplayName("§6Bank Note: §e$" + amount);
            meta.setLore(Arrays.asList("", "§bRight-click this note to redeem.", "§dThis note is worth: §e$" + amount));
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            itemStack.setItemMeta(meta);
            player.getInventory().addItem(itemStack);
            player.sendMessage(Prefixes.ECONOMY + "<nc>You have withdrawn <vc>$" + amount + " <nc>from your account and received a bank note.");
            plugin.getFCDatabase().saveTransaction(withdraw);
        } else if (cmd.getName().equalsIgnoreCase("balance")) {
            if (args.length == 0) {
                player.sendMessage(Prefixes.ECONOMY + "<nc>Your current balance is <vc>$" + player.getBalance());
            } else if (args.length == 1) {
                FirecraftPlayer target = plugin.getPlayerManager().getPlayer(args[0]);
                if (target == null) {
                    player.sendMessage(Prefixes.ECONOMY + "<ec>That player could not be found.");
                    return true;
                }

                player.sendMessage(Prefixes.ECONOMY + "<nc>The balance of <vc>" + target.getName() + " <nc>is <vc>$" + target.getBalance());
            } else {
                player.sendMessage(Prefixes.ECONOMY + "<ec>Invalid amount of arguments.");
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("baltop")) {
            List<Transaction> transactions = plugin.getFCDatabase().getTransactions();
            HashMap<UUID, Double> amounts = new HashMap<>();
            for (Transaction transaction : transactions) {
                if (amounts.containsKey(transaction.getPlayer())) {
                    if (transaction.getType().equals(TransactionType.DEPOSIT)) {
                        amounts.put(transaction.getPlayer(), amounts.get(transaction.getPlayer()) + transaction.getAmount());
                    } else if (transaction.getType().equals(TransactionType.WITHDRAWAL)) {
                        amounts.put(transaction.getPlayer(), amounts.get(transaction.getPlayer()) - transaction.getAmount());
                    }
                } else {
                    amounts.put(transaction.getPlayer(), transaction.getAmount());
                }
            }

            ArrayList<String> balances = new ArrayList<>();
            for (Map.Entry<UUID, Double> entry : amounts.entrySet()) {
                balances.add(entry.getKey().toString() + " " + entry.getValue());
            }

            balances.sort((a, b) -> {
                double aVal = Double.parseDouble(a.split(" ")[1]);
                double bVal = Double.parseDouble(b.split(" ")[1]);

                return Double.compare(aVal, bVal);
            });

            player.sendMessage(Prefixes.ECONOMY + "<nc>The top 10 balances");
            for (int i = (balances.size() > 10 ? 10 : balances.size()) - 1; i>=0; i--) {
                String line = balances.get(i);
                System.out.println(line);
                FirecraftPlayer p = plugin.getPlayerManager().getPlayer(UUID.fromString(line.split(" ")[0]));
                double amount = Double.parseDouble(line.split(" ")[1]);
                player.sendMessage(" &8- &a" + p.getName() + "&8: &c$" + amount);
            }
        }

        return true;
    }

    public Transaction deposit(FirecraftProfile profile, double amount) {
        Transaction transaction = new Transaction(profile.getUniqueId(), TransactionType.DEPOSIT, amount, System.currentTimeMillis());
        profile.addTransaction(transaction);
        return transaction;
    }

    public Transaction withdraw(FirecraftProfile profile, double amount) {
        Transaction transaction = new Transaction(profile.getUniqueId(), TransactionType.WITHDRAWAL, amount, System.currentTimeMillis());
        profile.addTransaction(transaction);
        return transaction;
    }
}
